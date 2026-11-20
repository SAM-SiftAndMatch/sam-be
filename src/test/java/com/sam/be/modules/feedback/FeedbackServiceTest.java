package com.sam.be.modules.feedback;

import static com.sam.be.modules.feedback.FeedbackDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.constant.enums.UserRole;
import com.sam.be.common.exception.ApiException;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

class FeedbackServiceTest {
    final FeedbackRepository repository = mock(FeedbackRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final FeedbackService service = new FeedbackService(repository, users);
    final UUID ownerId = UUID.randomUUID(), adminId = UUID.randomUUID(), key = UUID.randomUUID();
    final User owner =
            User.builder()
                    .id(ownerId)
                    .email("client@example.test")
                    .fullName("Khách hàng")
                    .role(UserRole.CLIENT)
                    .build();
    final User admin =
            User.builder()
                    .id(adminId)
                    .email("admin@example.test")
                    .fullName("Admin")
                    .role(UserRole.ADMIN)
                    .build();

    @BeforeEach
    void setup() {
        when(users.findByIdForFeedbackSubmission(ownerId)).thenReturn(Optional.of(owner));
        when(users.findById(adminId)).thenReturn(Optional.of(admin));
        when(repository.saveAndFlush(any()))
                .thenAnswer(
                        call -> {
                            PlatformFeedback item = call.getArgument(0);
                            if (item.getId() == null) item.setId(UUID.randomUUID());
                            return item;
                        });
    }

    @Test
    void acceptsBothParticipantRolesAndReturnsOnlyUserVisibleFields() throws Exception {
        for (UserRole role : List.of(UserRole.CLIENT, UserRole.FREELANCER)) {
            owner.setRole(role);
            var view =
                    service.create(
                            ownerId, new CreateInput(5, "  Tốt <script>alert(1)</script>  ", key));
            assertEquals(5, view.rating());
            assertEquals("Tốt <script>alert(1)</script>", view.comment());
            assertEquals(FeedbackStatus.NEW, view.status());
            assertNotNull(view.createdAt());
            assertFalse(
                    new ObjectMapper()
                            .findAndRegisterModules()
                            .writeValueAsString(view)
                            .contains("adminNote"));
        }
    }

    @Test
    void retryReturnsTheExistingRecordAndChangedPayloadIsAConflict() {
        var saved = item();
        when(repository.findByOwnerIdAndRequestKey(ownerId, key)).thenReturn(Optional.of(saved));
        assertEquals(saved.getId(), service.create(ownerId, new CreateInput(5, "Tốt", key)).id());
        assertEquals(
                409,
                assertThrows(
                                ResponseStatusException.class,
                                () -> service.create(ownerId, new CreateInput(1, "Khác", key)))
                        .getStatusCode()
                        .value());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsOutOfRangeRatingAndOversizedContentBeforeWriting() {
        for (Integer rating : new Integer[] {null, 0, 6})
            assertThrows(
                    ApiException.class,
                    () -> service.create(ownerId, new CreateInput(rating, "", key)));
        assertThrows(
                ApiException.class,
                () -> service.create(ownerId, new CreateInput(5, "x".repeat(2001), key)));
        assertThrows(
                ApiException.class, () -> service.create(ownerId, new CreateInput(5, "", null)));
        verifyNoInteractions(repository);
    }

    @Test
    void disabledUsersAndAdminsCannotSubmitUserReviews() {
        owner.setIsActive(false);
        assertThrows(
                ApiException.class, () -> service.create(ownerId, new CreateInput(5, "", key)));
        owner.setIsActive(true);
        owner.setRole(UserRole.ADMIN);
        assertThrows(
                ApiException.class, () -> service.create(ownerId, new CreateInput(5, "", key)));
        verifyNoInteractions(repository);
    }

    @Test
    void optionalCommentMayBeBlankAndHasABoundedLength() {
        assertEquals("", service.create(ownerId, new CreateInput(3, null, key)).comment());
        assertEquals(
                2000,
                service.create(ownerId, new CreateInput(3, "x".repeat(2000), key))
                        .comment()
                        .length());
    }

    @Test
    void historyAlwaysFiltersByTheAuthenticatedOwnerAndOmitsInternalNotes() {
        var item = item();
        item.setAdminNote("INTERNAL_ONLY");
        when(repository.findByOwnerId(eq(ownerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        var page = service.mine(ownerId, 0, 10);
        assertEquals(1, page.totalElements());
        assertEquals("Tốt", page.items().getFirst().comment());
        verify(repository).findByOwnerId(eq(ownerId), any(Pageable.class));
    }

    @Test
    void paginationAndAdminFiltersAreValidated() {
        assertThrows(ApiException.class, () -> service.mine(ownerId, -1, 10));
        assertThrows(ApiException.class, () -> service.mine(ownerId, 0, 101));
        assertThrows(ApiException.class, () -> service.mine(ownerId, 100001, 10));
        assertThrows(ApiException.class, () -> service.list(6, null, "", 0, 20));
        assertThrows(ApiException.class, () -> service.list(null, null, "x".repeat(101), 0, 20));
        verifyNoInteractions(repository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminListIncludesAuthorAndPrivateNotes() {
        var item = item();
        item.setAdminNote("Chờ kiểm tra");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        var result = service.list(5, FeedbackStatus.NEW, "khách", 0, 20).items().getFirst();
        assertEquals(ownerId, result.userId());
        assertEquals("client@example.test", result.userEmail());
        assertEquals("Chờ kiểm tra", result.adminNote());
    }

    @Test
    void adminUpdateRecordsActorWithoutChangingTheOriginalReview() {
        var item = item();
        when(repository.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
        var result =
                service.update(
                        adminId,
                        item.getId(),
                        new UpdateInput(FeedbackStatus.IN_PROGRESS, "  Đang kiểm tra  ", 0L));
        assertEquals(FeedbackStatus.IN_PROGRESS, result.status());
        assertEquals("Đang kiểm tra", result.adminNote());
        assertEquals("Admin", result.reviewedByName());
        assertEquals(5, result.rating());
        assertEquals("Tốt", result.comment());
        assertSame(admin, item.getReviewedBy());
    }

    @Test
    void staleAdminUpdateDoesNotOverwriteANewerDecision() {
        var item = item();
        item.setVersion(2);
        item.setAdminNote("Ghi chú mới");
        when(repository.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
        assertEquals(
                409,
                assertThrows(
                                ResponseStatusException.class,
                                () ->
                                        service.update(
                                                adminId,
                                                item.getId(),
                                                new UpdateInput(
                                                        FeedbackStatus.RESOLVED, "Ghi chú cũ", 0L)))
                        .getStatusCode()
                        .value());
        assertEquals("Ghi chú mới", item.getAdminNote());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void aNonAdminCannotUpdateFeedbackEvenWhenCallingTheServiceDirectly() {
        admin.setRole(UserRole.CLIENT);
        assertThrows(
                ApiException.class,
                () ->
                        service.update(
                                adminId,
                                UUID.randomUUID(),
                                new UpdateInput(FeedbackStatus.RESOLVED, "", 0L)));
        verifyNoInteractions(repository);
    }

    @Test
    void summaryIncludesAllRatingBucketsAndStatusCounts() {
        var row = mock(FeedbackRepository.SummaryRow.class);
        when(repository.summary()).thenReturn(row);
        when(row.getTotal()).thenReturn(3L);
        when(row.getAverageRating()).thenReturn(4.0);
        when(row.getNewCount()).thenReturn(2L);
        when(row.getResolvedCount()).thenReturn(1L);
        when(row.getFiveStars()).thenReturn(2L);
        var result = service.summary();
        assertEquals(3, result.total());
        assertEquals(4.0, result.averageRating());
        assertEquals(5, result.ratings().size());
        assertEquals(2, result.ratings().getLast().count());
    }

    @Test
    void searchTreatsWildcardCharactersAsLiteralText() {
        assertEquals("100\\%\\_done\\\\", FeedbackService.escapeLike("100%_done\\"));
    }

    private PlatformFeedback item() {
        var item = new PlatformFeedback();
        item.setId(UUID.randomUUID());
        item.setOwner(owner);
        item.setRequestKey(key);
        item.setRating(5);
        item.setComment("Tốt");
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(item.getCreatedAt());
        return item;
    }
}
