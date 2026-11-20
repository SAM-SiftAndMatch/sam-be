package com.sam.be.modules.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sam.be.common.client.GeminiClient;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.ai.dto.AdminAiDtos.*;
import com.sam.be.modules.ai.entity.AiApiKey;
import com.sam.be.modules.ai.entity.AiModel;
import com.sam.be.modules.ai.entity.AiTestStatus;
import com.sam.be.modules.ai.repository.AiApiKeyRepository;
import com.sam.be.modules.ai.repository.AiModelRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminAiConfigServiceTest {

    private final AiApiKeyRepository keyRepository = mock(AiApiKeyRepository.class);
    private final AiModelRepository modelRepository = mock(AiModelRepository.class);
    private final AiCryptoService cryptoService = new AiCryptoService("test-master-secret");
    private final AiCatalogService catalogService = mock(AiCatalogService.class);
    private final GeminiClient geminiClient = mock(GeminiClient.class);

    private final AdminAiConfigService service =
            new AdminAiConfigService(
                    keyRepository, modelRepository, cryptoService, catalogService, geminiClient);

    @Test
    void createKeyEncryptsMasksChecksDuplicateAndEvictsCache() {
        when(keyRepository.existsByKeyFingerprint(any())).thenReturn(false);
        when(keyRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            AiApiKey k = inv.getArgument(0);
                            k.setId(1L);
                            return k;
                        });

        AiKeyResponse created =
                service.createKey(new CreateAiKeyRequest("Main", "AIzaSySecret123456", 10, true));

        assertEquals("Main", created.label());
        assertEquals("AIza••••3456", created.keyMasked());
        assertEquals(10, created.priority());
        assertTrue(created.active());
        verify(catalogService).evictAll();

        when(keyRepository.existsByKeyFingerprint(any())).thenReturn(true);
        ApiException dup =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.createKey(
                                        new CreateAiKeyRequest(
                                                "Dup", "AIzaSySecret123456", 20, true)));
        assertEquals(ErrorCode.DUPLICATE_RESOURCE, dup.getErrorCode());
    }

    @Test
    void testSavedKeyUpdatesStatusAndFailCount() {
        AiApiKey entity = new AiApiKey();
        entity.setId(5L);
        entity.setKeyEncrypted(cryptoService.encrypt("AIzaSySavedKey9999"));
        entity.setKeyMasked("AIza••••9999");
        entity.setKeyFingerprint(AiCryptoService.fingerprint("AIzaSySavedKey9999"));
        when(keyRepository.findById(5L)).thenReturn(Optional.of(entity));
        when(catalogService.activeModels())
                .thenReturn(List.of("gemini-3.8-flash", "gemini-3.5-flash-lite"));
        when(geminiClient.testKeyConnection(
                        "AIzaSySavedKey9999", List.of("gemini-3.5-flash-lite", "gemini-3.8-flash")))
                .thenReturn(
                        new GeminiClient.TestOutcome(
                                false, 401, 120, "gemini-3.5-flash-lite", "Invalid"));

        AiTestResult fail = service.testSavedKey(5L, null);
        assertFalse(fail.ok());
        assertEquals(AiTestStatus.FAIL, entity.getLastTestStatus());
        assertEquals(1, entity.getFailCount());

        when(geminiClient.testKeyConnection(
                        "AIzaSySavedKey9999", List.of("gemini-3.5-flash-lite", "gemini-3.8-flash")))
                .thenReturn(
                        new GeminiClient.TestOutcome(true, 200, 95, "gemini-3.5-flash-lite", "OK"));
        AiTestResult ok = service.testSavedKey(5L, null);
        assertTrue(ok.ok());
        assertEquals(AiTestStatus.OK, entity.getLastTestStatus());
        assertEquals(0, entity.getFailCount());
    }

    @Test
    void createModelRejectsDuplicateAndTestModelUsesFallbackKey() {
        when(modelRepository.existsByModelNameIgnoreCase("gemini-2.5-flash")).thenReturn(true);
        ApiException dup =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.createModel(
                                        new CreateAiModelRequest(
                                                "gemini-2.5-flash", null, 10, true)));
        assertEquals(ErrorCode.DUPLICATE_RESOURCE, dup.getErrorCode());

        AiModel model = new AiModel();
        model.setId(2L);
        model.setModelName("gemini-2.5-pro");
        when(modelRepository.findById(2L)).thenReturn(Optional.of(model));
        when(catalogService.activeKeys()).thenReturn(List.of());
        when(catalogService.primaryEnvKeyOrNull()).thenReturn("AIzaSyEnvBackupKey");
        when(geminiClient.testConnection("AIzaSyEnvBackupKey", "gemini-2.5-pro"))
                .thenReturn(new GeminiClient.TestOutcome(true, 200, 210, "gemini-2.5-pro", "OK"));

        AiTestResult res = service.testSavedModel(2L, null);
        assertTrue(res.ok());
        assertEquals(AiTestStatus.OK, model.getLastTestStatus());
    }

    @Test
    void testModelsBatchRunsAllOrSelectedAndSummarizesResults() {
        AiModel m1 = new AiModel();
        m1.setId(1L);
        m1.setModelName("gemini-3.8-flash");
        AiModel m2 = new AiModel();
        m2.setId(2L);
        m2.setModelName("gemini-3.5-flash-lite");

        when(modelRepository.findAllByOrderByPriorityAscIdAsc()).thenReturn(List.of(m1, m2));
        when(modelRepository.findById(1L)).thenReturn(Optional.of(m1));
        when(modelRepository.findById(2L)).thenReturn(Optional.of(m2));
        when(catalogService.activeKeys())
                .thenReturn(List.of(new AiCatalogService.KeyCandidate(10, "fp", "raw-key")));
        when(geminiClient.testConnection("raw-key", "gemini-3.8-flash"))
                .thenReturn(
                        new GeminiClient.TestOutcome(
                                false, 503, 1200, "gemini-3.8-flash", "Quá tải"));
        when(geminiClient.testConnection("raw-key", "gemini-3.5-flash-lite"))
                .thenReturn(
                        new GeminiClient.TestOutcome(
                                true, 200, 310, "gemini-3.5-flash-lite", "OK"));

        BatchTestModelsResponse batch = service.testModelsBatch(null);
        assertEquals(2, batch.total());
        assertEquals(1, batch.passed());
        assertEquals(1, batch.failed());
        assertEquals(AiTestStatus.FAIL, m1.getLastTestStatus());
        assertEquals(AiTestStatus.OK, m2.getLastTestStatus());
    }
}
