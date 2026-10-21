package com.sam.be.modules.projectdraft.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ProjectDraftJsonCompatibilityTest {
    @Test
    void springJacksonMapperReadsNestedDraftState() {
        String json = """
                {"expectedRevision":1,"phase":"info","state":{
                  "projectName":"Ứng dụng bán hàng",
                  "upgrades":{"featured":true},
                  "selectedSkills":["Java","React"]
                }}
                """;

        UpdateProjectDraftRequest request = new JsonMapper().readValue(json, UpdateProjectDraftRequest.class);

        assertEquals(1L, request.expectedRevision());
        assertEquals("Ứng dụng bán hàng", request.state().get("projectName"));
        assertEquals(true, ((Map<?, ?>) request.state().get("upgrades")).get("featured"));
    }

    @Test
    void springJacksonMapperWritesDraftStateAsJsonObject() {
        ProjectDraftResponse response = new ProjectDraftResponse(
                UUID.randomUUID(), "MANUAL", "info", "EDITING", 0, 0, null, null,
                Map.of("projectName", "Ứng dụng bán hàng"), null, null,
                null, null, null, 0, 0, null, List.of());

        String json = new JsonMapper().writeValueAsString(response);

        assertEquals("Ứng dụng bán hàng",
                new JsonMapper().readTree(json).get("state").get("projectName").asString());
    }
}
