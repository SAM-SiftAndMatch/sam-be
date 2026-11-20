package com.sam.be.modules.feedback;

import static com.sam.be.modules.feedback.FeedbackDtos.*;

import com.sam.be.common.constant.enums.UserRole;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.response.PageRequests;
import com.sam.be.common.response.PageResponse;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FeedbackService {
    private final FeedbackRepository feedback;
    private final UserRepository users;

    @Transactional
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER')")
    public UserFeedback create(UUID userId, CreateInput input) {
        if (input == null
                || input.rating() == null
                || input.rating() < 1
                || input.rating() > 5
                || input.requestKey() == null) throw invalid("Chọn số sao từ 1 đến 5.");
        String comment = text(input.comment());
        // Serialize same-user submissions so a retried request cannot insert a second review.
        User user =
                users.findByIdForFeedbackSubmission(userId)
                        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        requireActive(user);
        if (user.getRole() != UserRole.CLIENT && user.getRole() != UserRole.FREELANCER)
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        var existing = feedback.findByOwnerIdAndRequestKey(userId, input.requestKey());
        if (existing.isPresent()) {
            var old = existing.get();
            if (old.getRating() != input.rating() || !old.getComment().equals(comment))
                throw conflict(
                        "Yêu cầu này đã được gửi với nội dung khác. Vui lòng tải lại trang để gửi góp ý mới.");
            return userView(old);
        }
        var item = new PlatformFeedback();
        item.setOwner(user);
        item.setRequestKey(input.requestKey());
        item.setRating(input.rating());
        item.setComment(comment);
        Instant now = Instant.now();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return userView(feedback.saveAndFlush(item));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER')")
    public PageResponse<UserFeedback> mine(UUID userId, int page, int size) {
        return PageResponse.from(
                feedback.findByOwnerId(userId, pageable(page, size)).map(this::userView));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AdminFeedback> list(
            Integer rating, FeedbackStatus status, String query, int page, int size) {
        if (rating != null && (rating < 1 || rating > 5)) throw invalid("Số sao phải từ 1 đến 5.");
        String keyword = query == null ? "" : query.strip();
        if (keyword.length() > 100) throw invalid("Từ khóa tìm kiếm tối đa 100 ký tự.");
        Specification<PlatformFeedback> filter =
                (root, criteria, builder) -> {
                    var predicates = new ArrayList<Predicate>();
                    if (rating != null) predicates.add(builder.equal(root.get("rating"), rating));
                    if (status != null) predicates.add(builder.equal(root.get("status"), status));
                    if (!keyword.isEmpty()) {
                        String pattern = "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
                        var owner = root.join("owner");
                        predicates.add(
                                builder.or(
                                        builder.like(
                                                builder.lower(root.get("comment")), pattern, '\\'),
                                        builder.like(
                                                builder.lower(owner.get("fullName")),
                                                pattern,
                                                '\\'),
                                        builder.like(
                                                builder.lower(owner.get("email")), pattern, '\\')));
                    }
                    return builder.and(predicates.toArray(Predicate[]::new));
                };
        return PageResponse.from(
                feedback.findAll(filter, pageable(page, size)).map(this::adminView));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AdminFeedback detail(UUID id) {
        return adminView(
                feedback.findById(id)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Summary summary() {
        var row = feedback.summary();
        return new Summary(
                row.getTotal(),
                row.getAverageRating(),
                row.getNewCount(),
                row.getInProgressCount(),
                row.getResolvedCount(),
                List.of(
                        new RatingCount(1, row.getOneStar()),
                        new RatingCount(2, row.getTwoStars()),
                        new RatingCount(3, row.getThreeStars()),
                        new RatingCount(4, row.getFourStars()),
                        new RatingCount(5, row.getFiveStars())));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminFeedback update(UUID actorId, UUID id, UpdateInput input) {
        if (input == null
                || input.status() == null
                || input.version() == null
                || input.version() < 0)
            throw invalid("Trạng thái và phiên bản đánh giá không hợp lệ.");
        String note = text(input.adminNote());
        User actor =
                users.findById(actorId)
                        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        requireActive(actor);
        if (actor.getRole() != UserRole.ADMIN) throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        var item =
                feedback.findByIdForUpdate(id)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (item.getVersion() != input.version())
            throw conflict("Đánh giá đã được admin khác cập nhật. Vui lòng tải lại trước khi lưu.");
        if (item.getStatus() == input.status() && item.getAdminNote().equals(note))
            return adminView(item);
        item.setStatus(input.status());
        item.setAdminNote(note);
        item.setReviewedBy(actor);
        item.setUpdatedAt(Instant.now());
        return adminView(feedback.saveAndFlush(item));
    }

    private UserFeedback userView(PlatformFeedback item) {
        return new UserFeedback(
                item.getId(),
                item.getRating(),
                item.getComment(),
                item.getStatus(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

    private AdminFeedback adminView(PlatformFeedback item) {
        User owner = item.getOwner();
        return new AdminFeedback(
                item.getId(),
                item.getRating(),
                item.getComment(),
                item.getStatus(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                owner.getId(),
                owner.getFullName(),
                owner.getEmail(),
                owner.getRole(),
                item.getAdminNote(),
                item.getReviewedBy() == null ? null : item.getReviewedBy().getFullName(),
                item.getVersion());
    }

    private org.springframework.data.domain.Pageable pageable(int page, int size) {
        if (page > 100000) throw invalid("Số trang không hợp lệ.");
        return PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private String text(String value) {
        if (value != null && value.length() > 2000) throw invalid("Nội dung tối đa 2000 ký tự.");
        return value == null ? "" : value.strip();
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private void requireActive(User user) {
        if (!Boolean.TRUE.equals(user.getIsActive()))
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
