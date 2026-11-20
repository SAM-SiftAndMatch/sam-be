package com.sam.be.modules.ai.service;

import static com.sam.be.modules.ai.dto.AdminAiDtos.*;

import com.sam.be.common.client.GeminiClient;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.ai.entity.AiApiKey;
import com.sam.be.modules.ai.entity.AiModel;
import com.sam.be.modules.ai.entity.AiTestStatus;
import com.sam.be.modules.ai.repository.AiApiKeyRepository;
import com.sam.be.modules.ai.repository.AiModelRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAiConfigService {

    private final AiApiKeyRepository keyRepository;
    private final AiModelRepository modelRepository;
    private final AiCryptoService cryptoService;
    private final AiCatalogService catalogService;
    private final GeminiClient geminiClient;

    @Transactional(readOnly = true)
    public RuntimeStatus runtimeStatus() {
        int activeKeys = (int) keyRepository.findByActiveTrueOrderByPriorityAscIdAsc().size();
        int activeModels = (int) modelRepository.findByActiveTrueOrderByPriorityAscIdAsc().size();
        return new RuntimeStatus(
                cryptoService.isConfigured(),
                catalogService.envFallbackKeyMasked(),
                catalogService.envFallbackModel(),
                activeKeys,
                activeModels);
    }

    // ---------- Keys ----------

    @Transactional(readOnly = true)
    public List<AiKeyResponse> listKeys() {
        return keyRepository.findAllByOrderByPriorityAscIdAsc().stream()
                .map(this::toKeyResponse)
                .toList();
    }

    @Transactional
    public AiKeyResponse createKey(CreateAiKeyRequest req) {
        String raw = req.apiKey().trim();
        String fingerprint = AiCryptoService.fingerprint(raw);
        if (keyRepository.existsByKeyFingerprint(fingerprint)) {
            throw new ApiException(
                    ErrorCode.DUPLICATE_RESOURCE, "API key này đã tồn tại trong hệ thống");
        }
        AiApiKey entity = new AiApiKey();
        entity.setLabel(trimToNull(req.label()));
        entity.setKeyEncrypted(cryptoService.encrypt(raw));
        entity.setKeyMasked(AiCryptoService.mask(raw));
        entity.setKeyFingerprint(fingerprint);
        entity.setPriority(req.priority() != null ? req.priority() : 100);
        entity.setActive(req.active() == null || req.active());

        AiApiKey saved = keyRepository.save(entity);
        catalogService.evictAll();
        return toKeyResponse(saved);
    }

    @Transactional
    public AiKeyResponse updateKey(Long id, UpdateAiKeyRequest req) {
        AiApiKey entity =
                keyRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy API key"));
        if (req.label() != null) {
            entity.setLabel(trimToNull(req.label()));
        }
        if (req.priority() != null) {
            entity.setPriority(req.priority());
        }
        if (req.active() != null) {
            entity.setActive(req.active());
        }
        AiApiKey saved = keyRepository.save(entity);
        catalogService.evictAll();
        return toKeyResponse(saved);
    }

    @Transactional
    public void deleteKey(Long id) {
        AiApiKey entity =
                keyRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy API key"));
        keyRepository.delete(entity);
        catalogService.evictAll();
    }

    public AiTestResult testSavedKey(Long id, TestKeyRequest req) {
        AiApiKey entity =
                keyRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy API key"));
        String rawKey = cryptoService.decrypt(entity.getKeyEncrypted());
        List<String> candidateModels =
                resolveModelsForKeyTest(req != null ? req.modelName() : null);

        GeminiClient.TestOutcome outcome = geminiClient.testKeyConnection(rawKey, candidateModels);
        recordKeyTestResult(id, outcome);
        return toTestResult(outcome);
    }

    public AiTestResult testDraftKey(TestDraftKeyRequest req) {
        List<String> candidateModels = resolveModelsForKeyTest(req.modelName());
        GeminiClient.TestOutcome outcome =
                geminiClient.testKeyConnection(req.apiKey().trim(), candidateModels);
        return toTestResult(outcome);
    }

    // ---------- Models ----------

    @Transactional(readOnly = true)
    public List<AiModelResponse> listModels() {
        return modelRepository.findAllByOrderByPriorityAscIdAsc().stream()
                .map(this::toModelResponse)
                .toList();
    }

    @Transactional
    public AiModelResponse createModel(CreateAiModelRequest req) {
        String name = req.modelName().trim();
        if (modelRepository.existsByModelNameIgnoreCase(name)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Model '" + name + "' đã tồn tại");
        }
        AiModel entity = new AiModel();
        entity.setModelName(name);
        entity.setDisplayName(trimToNull(req.displayName()));
        entity.setPriority(req.priority() != null ? req.priority() : 100);
        entity.setActive(req.active() == null || req.active());

        AiModel saved = modelRepository.save(entity);
        catalogService.evictAll();
        return toModelResponse(saved);
    }

    @Transactional
    public AiModelResponse updateModel(Long id, UpdateAiModelRequest req) {
        AiModel entity =
                modelRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy model"));
        if (req.displayName() != null) {
            entity.setDisplayName(trimToNull(req.displayName()));
        }
        if (req.priority() != null) {
            entity.setPriority(req.priority());
        }
        if (req.active() != null) {
            entity.setActive(req.active());
        }
        AiModel saved = modelRepository.save(entity);
        catalogService.evictAll();
        return toModelResponse(saved);
    }

    @Transactional
    public void deleteModel(Long id) {
        AiModel entity =
                modelRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy model"));
        modelRepository.delete(entity);
        catalogService.evictAll();
    }

    public AiTestResult testSavedModel(Long id, TestModelRequest req) {
        AiModel entity =
                modelRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "Không tìm thấy model"));
        String rawKey = resolveKeyForModelTest(req != null ? req.keyId() : null);
        GeminiClient.TestOutcome outcome =
                geminiClient.testConnection(rawKey, entity.getModelName());
        recordModelTestResult(id, outcome);
        return toTestResult(outcome);
    }

    public AiTestResult testDraftModel(TestDraftModelRequest req) {
        String rawKey = resolveKeyForModelTest(req.keyId());
        GeminiClient.TestOutcome outcome =
                geminiClient.testConnection(rawKey, req.modelName().trim());
        return toTestResult(outcome);
    }

    public BatchTestModelsResponse testModelsBatch(BatchTestModelsRequest req) {
        String rawKey = resolveKeyForModelTest(req != null ? req.keyId() : null);
        List<AiModel> allOrdered = modelRepository.findAllByOrderByPriorityAscIdAsc();
        List<AiModel> targets;
        if (req != null && req.modelIds() != null && !req.modelIds().isEmpty()) {
            Set<Long> wanted = Set.copyOf(req.modelIds());
            targets = allOrdered.stream().filter(m -> wanted.contains(m.getId())).toList();
        } else {
            targets = allOrdered;
        }
        if (targets.isEmpty()) {
            return new BatchTestModelsResponse(0, 0, 0, List.of());
        }

        int poolSize = Math.min(4, targets.size());
        List<BatchModelTestItem> items = new ArrayList<>(targets.size());
        try (ExecutorService pool = Executors.newFixedThreadPool(poolSize)) {
            List<CompletableFuture<BatchModelTestItem>> futures =
                    targets.stream()
                            .map(
                                    model ->
                                            CompletableFuture.supplyAsync(
                                                    () -> {
                                                        GeminiClient.TestOutcome outcome =
                                                                geminiClient.testConnection(
                                                                        rawKey,
                                                                        model.getModelName());
                                                        recordModelTestResult(
                                                                model.getId(), outcome);
                                                        return new BatchModelTestItem(
                                                                model.getId(),
                                                                model.getModelName(),
                                                                toTestResult(outcome));
                                                    },
                                                    pool))
                            .toList();
            for (var future : futures) {
                items.add(future.join());
            }
        }
        int passed = (int) items.stream().filter(i -> i.result().ok()).count();
        return new BatchTestModelsResponse(items.size(), passed, items.size() - passed, items);
    }

    // ---------- Helpers ----------

    private void recordKeyTestResult(Long id, GeminiClient.TestOutcome outcome) {
        keyRepository
                .findById(id)
                .ifPresent(
                        entity -> {
                            entity.setLastTestAt(Instant.now());
                            entity.setLastTestStatus(
                                    outcome.ok() ? AiTestStatus.OK : AiTestStatus.FAIL);
                            entity.setLastTestMessage(clip(outcome.message(), 500));
                            entity.setFailCount(outcome.ok() ? 0 : entity.getFailCount() + 1);
                            keyRepository.save(entity);
                        });
    }

    private void recordModelTestResult(Long id, GeminiClient.TestOutcome outcome) {
        modelRepository
                .findById(id)
                .ifPresent(
                        entity -> {
                            entity.setLastTestAt(Instant.now());
                            entity.setLastTestStatus(
                                    outcome.ok() ? AiTestStatus.OK : AiTestStatus.FAIL);
                            entity.setLastTestMessage(clip(outcome.message(), 500));
                            modelRepository.save(entity);
                        });
    }

    private List<String> resolveModelsForKeyTest(String requestedModel) {
        if (requestedModel != null && !requestedModel.isBlank()) {
            return List.of(requestedModel.trim());
        }
        List<String> active = catalogService.activeModels();
        List<String> liteFirst = new ArrayList<>();
        for (String m : active) {
            if (m.toLowerCase(java.util.Locale.ROOT).contains("flash-lite")) {
                liteFirst.add(m);
            }
        }
        for (String m : active) {
            if (!liteFirst.contains(m)) {
                liteFirst.add(m);
            }
        }
        if (liteFirst.isEmpty()) {
            liteFirst.add("gemini-3.5-flash-lite");
        }
        return liteFirst;
    }

    private String resolveKeyForModelTest(Long keyId) {
        if (keyId != null) {
            AiApiKey key =
                    keyRepository
                            .findById(keyId)
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    ErrorCode.RESOURCE_NOT_FOUND,
                                                    "Không tìm thấy API key"));
            return cryptoService.decrypt(key.getKeyEncrypted());
        }
        List<AiCatalogService.KeyCandidate> keys = catalogService.activeKeys();
        if (!keys.isEmpty()) {
            return keys.getFirst().rawKey();
        }
        String envKey = catalogService.primaryEnvKeyOrNull();
        if (envKey != null) {
            return envKey;
        }
        throw new ApiException(
                ErrorCode.REQUEST_FAILED,
                "Chưa có API key nào đang hoạt động (hoặc chưa có GEMINI_API_KEY trong .env) để test model");
    }

    private AiKeyResponse toKeyResponse(AiApiKey k) {
        return new AiKeyResponse(
                k.getId(),
                k.getLabel(),
                k.getKeyMasked(),
                k.isActive(),
                k.getPriority(),
                k.getFailCount(),
                k.getLastTestAt(),
                k.getLastTestStatus(),
                k.getLastTestMessage(),
                k.getCreatedAt(),
                k.getUpdatedAt());
    }

    private AiModelResponse toModelResponse(AiModel m) {
        return new AiModelResponse(
                m.getId(),
                m.getModelName(),
                m.getDisplayName(),
                m.isActive(),
                m.getPriority(),
                m.getLastTestAt(),
                m.getLastTestStatus(),
                m.getLastTestMessage(),
                m.getCreatedAt(),
                m.getUpdatedAt());
    }

    private static AiTestResult toTestResult(GeminiClient.TestOutcome outcome) {
        return new AiTestResult(
                outcome.ok(),
                outcome.statusCode(),
                outcome.latencyMs(),
                outcome.modelUsed(),
                outcome.message());
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String clip(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
