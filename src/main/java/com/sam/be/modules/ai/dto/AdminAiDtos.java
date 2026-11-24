package com.sam.be.modules.ai.dto;

import com.sam.be.modules.ai.entity.AiTestStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AdminAiDtos {

    private AdminAiDtos() {}

    public record RuntimeStatus(
            boolean cryptoConfigured,
            String envFallbackKeyMasked,
            String envFallbackModel,
            int activeDbKeys,
            int activeDbModels) {}

    public record AiKeyResponse(
            Long id,
            String label,
            String keyMasked,
            boolean active,
            int priority,
            int failCount,
            Instant lastTestAt,
            AiTestStatus lastTestStatus,
            String lastTestMessage,
            Instant createdAt,
            Instant updatedAt) {}

    public record CreateAiKeyRequest(
            @Size(max = 100, message = "Nhãn tối đa 100 ký tự") String label,
            @NotBlank(message = "API key không được để trống")
                    @Size(max = 2000, message = "API key tối đa 2000 ký tự")
                    String apiKey,
            @Min(value = 0, message = "Priority từ 0 đến 10000")
                    @Max(value = 10000, message = "Priority từ 0 đến 10000")
                    Integer priority,
            Boolean active) {}

    public record UpdateAiKeyRequest(
            @Size(max = 100, message = "Nhãn tối đa 100 ký tự") String label,
            @Min(value = 0, message = "Priority từ 0 đến 10000")
                    @Max(value = 10000, message = "Priority từ 0 đến 10000")
                    Integer priority,
            Boolean active) {}

    public record TestKeyRequest(
            @Size(max = 120, message = "Tên model tối đa 120 ký tự")
                    @Pattern(
                            regexp = "^$|^[A-Za-z0-9._-]+$",
                            message = "Tên model chỉ chứa chữ, số, dấu chấm, gạch ngang, gạch dưới")
                    String modelName) {}

    public record TestDraftKeyRequest(
            @NotBlank(message = "API key không được để trống")
                    @Size(max = 2000, message = "API key tối đa 2000 ký tự")
                    String apiKey,
            @Size(max = 120, message = "Tên model tối đa 120 ký tự")
                    @Pattern(
                            regexp = "^$|^[A-Za-z0-9._-]+$",
                            message = "Tên model chỉ chứa chữ, số, dấu chấm, gạch ngang, gạch dưới")
                    String modelName) {}

    public record AiModelResponse(
            Long id,
            String modelName,
            String displayName,
            boolean active,
            int priority,
            Instant lastTestAt,
            AiTestStatus lastTestStatus,
            String lastTestMessage,
            Instant createdAt,
            Instant updatedAt) {}

    public record CreateAiModelRequest(
            @NotBlank(message = "Tên model không được để trống")
                    @Size(max = 120, message = "Tên model tối đa 120 ký tự")
                    @Pattern(
                            regexp = "^[A-Za-z0-9._-]+$",
                            message = "Tên model chỉ chứa chữ, số, dấu chấm, gạch ngang, gạch dưới")
                    String modelName,
            @Size(max = 120, message = "Tên hiển thị tối đa 120 ký tự") String displayName,
            @Min(value = 0, message = "Priority từ 0 đến 10000")
                    @Max(value = 10000, message = "Priority từ 0 đến 10000")
                    Integer priority,
            Boolean active) {}

    public record UpdateAiModelRequest(
            @Size(max = 120, message = "Tên hiển thị tối đa 120 ký tự") String displayName,
            @Min(value = 0, message = "Priority từ 0 đến 10000")
                    @Max(value = 10000, message = "Priority từ 0 đến 10000")
                    Integer priority,
            Boolean active) {}

    public record TestModelRequest(Long keyId) {}

    public record TestDraftModelRequest(
            @NotBlank(message = "Tên model không được để trống")
                    @Size(max = 120, message = "Tên model tối đa 120 ký tự")
                    @Pattern(
                            regexp = "^[A-Za-z0-9._-]+$",
                            message = "Tên model chỉ chứa chữ, số, dấu chấm, gạch ngang, gạch dưới")
                    String modelName,
            Long keyId) {}

    public record AiTestResult(
            boolean ok, Integer statusCode, long latencyMs, String modelUsed, String message) {}

    public record BatchTestModelsRequest(java.util.List<Long> modelIds, Long keyId) {}

    public record BatchModelTestItem(Long modelId, String modelName, AiTestResult result) {}

    public record BatchTestModelsResponse(
            int total, int passed, int failed, java.util.List<BatchModelTestItem> items) {}
}
