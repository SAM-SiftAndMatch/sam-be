package com.sam.be.modules.feedback;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.UserRepository;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

class FeedbackRepositoryMappingTest {
    @Test
    void entityMappingsAndDeclaredRepositoryQueriesBootstrapWithoutDatabaseAccess() {
        try (var factory =
                        new Configuration()
                                .addAnnotatedClass(User.class)
                                .addAnnotatedClass(PlatformFeedback.class)
                                .setProperty(
                                        "hibernate.dialect",
                                        "org.hibernate.dialect.PostgreSQLDialect")
                                .setProperty("hibernate.boot.allow_jdbc_metadata_access", "false")
                                .setProperty(
                                        "hibernate.connection.provider_class",
                                        "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
                                .setProperty("hibernate.hbm2ddl.auto", "none")
                                .buildSessionFactory();
                var manager = factory.createEntityManager()) {
            var repositories = new JpaRepositoryFactory(manager);
            assertNotNull(repositories.getRepository(FeedbackRepository.class));
            assertNotNull(repositories.getRepository(UserRepository.class));
        }
    }
}
