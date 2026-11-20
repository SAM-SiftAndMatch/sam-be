package com.sam.be.modules.feedback;

import static com.sam.be.modules.feedback.FeedbackDtos.*;
import static org.junit.jupiter.api.Assertions.*;

import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Function;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

/** Uses only a dedicated random test schema, never application tables. */
@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class FeedbackPostgresTest {
    SessionFactory factory;
    JdbcTemplate database;
    String schema;
    UUID client = UUID.randomUUID(), freelancer = UUID.randomUUID(), admin = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        String url = System.getenv("FINANCE_TEST_DATABASE_URL");
        String username = System.getenv().getOrDefault("FINANCE_TEST_DATABASE_USER", "postgres");
        String password = System.getenv().getOrDefault("FINANCE_TEST_DATABASE_PASSWORD", "");
        schema = "feedback_test_" + UUID.randomUUID().toString().replace("-", "");
        var source = new DriverManagerDataSource(url, username, password);
        database = new JdbcTemplate(source);
        database.execute("CREATE SCHEMA " + schema);
        String scopedUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        var scoped = new JdbcTemplate(new DriverManagerDataSource(scopedUrl, username, password));
        scoped.execute(
                """
            CREATE TABLE users (
                id UUID PRIMARY KEY, email VARCHAR(255) NOT NULL UNIQUE,
                password_hash VARCHAR(255), full_name VARCHAR(255) NOT NULL,
                contract_name VARCHAR(255), contact_email VARCHAR(255), phone_number VARCHAR(255),
                policy_accepted_at TIMESTAMPTZ, policy_version VARCHAR(255),
                role VARCHAR(255) NOT NULL, is_active BOOLEAN NOT NULL DEFAULT true,
                created_at TIMESTAMP, updated_at TIMESTAMP
            )
            """);
        try (var input =
                getClass().getResourceAsStream("/db/migration/V48__Platform_Feedback.sql")) {
            assertNotNull(input);
            scoped.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        for (var user :
                new Object[][] {
                    {client, "CLIENT", "Khách hàng"},
                    {freelancer, "FREELANCER", "Freelancer"},
                    {admin, "ADMIN", "Admin"}
                })
            scoped.update(
                    "INSERT INTO users(id,email,full_name,role) VALUES(?,?,?,?)",
                    user[0],
                    user[0] + "@example.test",
                    user[2],
                    user[1]);
        factory =
                new Configuration()
                        .addAnnotatedClass(User.class)
                        .addAnnotatedClass(PlatformFeedback.class)
                        .setProperty("hibernate.connection.url", scopedUrl)
                        .setProperty("hibernate.connection.username", username)
                        .setProperty("hibernate.connection.password", password)
                        .setProperty("hibernate.hbm2ddl.auto", "validate")
                        .setProperty("hibernate.show_sql", "false")
                        .setProperty("hibernate.connection.pool_size", "4")
                        .buildSessionFactory();
    }

    @AfterEach
    void cleanup() {
        if (factory != null) factory.close();
        if (database != null && schema != null)
            database.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    @Test
    void migrationJpaQueriesFiltersSummaryAndVersionedAdminUpdatesWorkTogether() {
        UUID key = UUID.randomUUID();
        var created =
                transaction(
                        service ->
                                service.create(
                                        client, new CreateInput(5, "Giao diện 100%_tốt\\", key)));
        var retry =
                transaction(
                        service ->
                                service.create(
                                        client, new CreateInput(5, "Giao diện 100%_tốt\\", key)));
        assertEquals(created.id(), retry.id());
        transaction(
                service ->
                        service.create(
                                freelancer, new CreateInput(1, "Cần sửa", UUID.randomUUID())));
        var matches = transaction(service -> service.list(null, null, "%_", 0, 20));
        assertEquals(1, matches.totalElements());
        assertEquals(created.id(), matches.items().getFirst().id());
        var saved =
                transaction(
                        service ->
                                service.update(
                                        admin,
                                        created.id(),
                                        new UpdateInput(
                                                FeedbackStatus.RESOLVED, "INTERNAL_ONLY", 0L)));
        assertEquals(1, saved.version());
        assertEquals("Admin", saved.reviewedByName());
        assertEquals(
                1,
                transaction(service -> service.list(5, FeedbackStatus.RESOLVED, "", 0, 20))
                        .totalElements());
        var history = transaction(service -> service.mine(client, 0, 10));
        assertEquals(1, history.totalElements());
        assertEquals(FeedbackStatus.RESOLVED, history.items().getFirst().status());
        assertEquals(
                409,
                assertThrows(
                                ResponseStatusException.class,
                                () ->
                                        transaction(
                                                service ->
                                                        service.update(
                                                                admin,
                                                                created.id(),
                                                                new UpdateInput(
                                                                        FeedbackStatus.NEW,
                                                                        "Old",
                                                                        0L))))
                        .getStatusCode()
                        .value());
        var summary = transaction(FeedbackService::summary);
        assertEquals(2, summary.total());
        assertEquals(3, summary.averageRating());
        assertEquals(1, summary.resolvedCount());
    }

    @Test
    void simultaneousRetriesCreateOnlyOneReview() throws Exception {
        UUID key = UUID.randomUUID();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<UserFeedback> create =
                    () -> {
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        return transaction(
                                service ->
                                        service.create(client, new CreateInput(5, "Góp ý", key)));
                    };
            var first = pool.submit(create);
            var second = pool.submit(create);
            start.countDown();
            assertEquals(
                    first.get(20, TimeUnit.SECONDS).id(), second.get(20, TimeUnit.SECONDS).id());
        }
        assertEquals(1, transaction(service -> service.mine(client, 0, 10)).totalElements());
    }

    @Test
    void databaseRejectsInvalidRatings() throws Exception {
        try (var connection =
                        DriverManager.getConnection(
                                System.getenv("FINANCE_TEST_DATABASE_URL"),
                                System.getenv()
                                        .getOrDefault("FINANCE_TEST_DATABASE_USER", "postgres"),
                                System.getenv()
                                        .getOrDefault("FINANCE_TEST_DATABASE_PASSWORD", ""));
                var statement = connection.createStatement()) {
            assertThrows(
                    java.sql.SQLException.class,
                    () ->
                            statement.execute(
                                    "INSERT INTO "
                                            + schema
                                            + ".platform_feedback(id,user_id,request_key,rating) VALUES ('"
                                            + UUID.randomUUID()
                                            + "','"
                                            + client
                                            + "','"
                                            + UUID.randomUUID()
                                            + "',6)"));
        }
    }

    private <T> T transaction(Function<FeedbackService, T> work) {
        try (EntityManager manager = factory.createEntityManager()) {
            manager.getTransaction().begin();
            try {
                var repositories = new JpaRepositoryFactory(manager);
                var service =
                        new FeedbackService(
                                repositories.getRepository(FeedbackRepository.class),
                                repositories.getRepository(UserRepository.class));
                T result = work.apply(service);
                manager.getTransaction().commit();
                return result;
            } catch (RuntimeException error) {
                manager.getTransaction().rollback();
                throw error;
            }
        }
    }
}
