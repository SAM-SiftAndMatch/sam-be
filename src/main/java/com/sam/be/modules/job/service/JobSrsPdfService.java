package com.sam.be.modules.job.service;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobSrsPdfService {
    private final JdbcTemplate jdbc;
    private final SrsPdfRenderer renderer;

    public record Status(String status, int attempts) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(UUID jobId) {
        jdbc.update("INSERT INTO job_srs_pdfs (job_id) VALUES (?) ON CONFLICT (job_id) DO NOTHING", jobId);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void recoverMissing() {
        try {
            int queued = jdbc.update("""
                    INSERT INTO job_srs_pdfs (job_id)
                    SELECT j.id FROM jobs j
                    WHERE j.srs_content IS NOT NULL AND BTRIM(j.srs_content) <> ''
                      AND NOT EXISTS (SELECT 1 FROM job_srs_pdfs p WHERE p.job_id = j.id)
                    ORDER BY j.created_at, j.id
                    LIMIT 20
                    ON CONFLICT (job_id) DO NOTHING
                    """);
            if (queued > 0) log.info("Recovered {} missing SRS PDF queue entries", queued);
        } catch (Exception ex) {
            log.warn("Could not recover missing SRS PDF queue entries; will retry", ex);
        }
    }

    public Status status(UUID jobId) {
        var rows = jdbc.query("SELECT status, attempts FROM job_srs_pdfs WHERE job_id = ?",
                (rs, row) -> new Status(rs.getString(1), rs.getInt(2)), jobId);
        return rows.isEmpty() ? new Status("NONE", 0) : rows.getFirst();
    }

    public byte[] pdf(UUID jobId) {
        var rows = jdbc.query("SELECT pdf_data FROM job_srs_pdfs WHERE job_id = ? AND status = 'READY'",
                (rs, row) -> rs.getBytes(1), jobId);
        if (rows.isEmpty()) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "PDF SRS chưa sẵn sàng.");
        return rows.getFirst();
    }

    public void retry(UUID jobId) {
        int changed = jdbc.update("UPDATE job_srs_pdfs SET status = 'PENDING', attempts = 0, next_attempt_at = CURRENT_TIMESTAMP, started_at = NULL, error_message = NULL, updated_at = CURRENT_TIMESTAMP WHERE job_id = ? AND status = 'FAILED'", jobId);
        if (changed == 0) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Không có PDF lỗi để thử lại.");
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void generatePending() {
        try {
            // Atomic claim; SKIP LOCKED lets multiple backend instances run safely.
            var claimed = jdbc.query("""
                    WITH candidate AS (
                      SELECT job_id FROM job_srs_pdfs
                      WHERE ((status IN ('PENDING', 'FAILED') AND attempts < 5 AND next_attempt_at <= CURRENT_TIMESTAMP)
                         OR (status = 'GENERATING' AND started_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes'))
                      ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED
                    )
                    UPDATE job_srs_pdfs p SET status = 'GENERATING', attempts = attempts + 1,
                      started_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                    FROM candidate c WHERE p.job_id = c.job_id RETURNING p.job_id, p.attempts
                    """, (rs, row) -> new Claim(rs.getObject(1, UUID.class), rs.getInt(2)));
            if (claimed.isEmpty()) return;
            Claim claim = claimed.getFirst();
            try {
                var sources = jdbc.query("SELECT title, srs_content FROM jobs WHERE id = ?",
                        (rs, row) -> new Source(rs.getString(1), rs.getString(2)), claim.jobId());
                if (sources.isEmpty()) throw new IllegalStateException("Job not found");
                byte[] bytes = renderer.render(sources.getFirst().title(), sources.getFirst().content());
                if (bytes.length > 2_000_000) throw new IllegalStateException("PDF exceeds 2 MB");
                jdbc.update("UPDATE job_srs_pdfs SET status = 'READY', pdf_data = ?, started_at = NULL, error_message = NULL, updated_at = CURRENT_TIMESTAMP WHERE job_id = ? AND status = 'GENERATING' AND attempts = ?",
                        bytes, claim.jobId(), claim.attempts());
            } catch (Exception ex) {
                log.warn("SRS PDF generation failed for job {} (attempt {})", claim.jobId(), claim.attempts(), ex);
                long delaySeconds = Math.min(300, 10L * (1L << Math.min(claim.attempts() - 1, 5)));
                jdbc.update("UPDATE job_srs_pdfs SET status = 'FAILED', started_at = NULL, error_message = ?, next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'), updated_at = CURRENT_TIMESTAMP WHERE job_id = ? AND status = 'GENERATING' AND attempts = ?",
                        String.valueOf(ex.getMessage()).substring(0, Math.min(500, String.valueOf(ex.getMessage()).length())),
                        delaySeconds, claim.jobId(), claim.attempts());
            }
        } catch (Exception ex) {
            log.error("SRS PDF worker failed", ex);
        }
    }

    private record Claim(UUID jobId, int attempts) {}
    private record Source(String title, String content) {}
}
