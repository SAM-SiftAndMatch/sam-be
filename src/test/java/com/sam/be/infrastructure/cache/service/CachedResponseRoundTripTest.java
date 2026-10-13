package com.sam.be.infrastructure.cache.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.config.AppConfig;
import com.sam.be.common.constant.enums.ContractStatus;
import com.sam.be.common.constant.enums.DeliveryStatus;
import com.sam.be.common.constant.enums.ProjectTaskStatus;
import com.sam.be.common.constant.enums.ProposalStatus;
import com.sam.be.common.constant.enums.RecommendationStatus;
import com.sam.be.common.response.PageResponse;
import com.sam.be.modules.contract.dto.response.ContractDetailResponse;
import com.sam.be.modules.contract.dto.response.ContractRevisionResponse;
import com.sam.be.modules.job.dto.response.AiRecommendationResponse;
import com.sam.be.modules.job.dto.response.SkillExperienceDto;
import com.sam.be.modules.proposal.dto.response.ProposalResponse;
import com.sam.be.modules.workflow.dto.response.ProjectDeliveryResponse;
import com.sam.be.modules.workflow.dto.response.ProjectTimelineResponse;
import com.sam.be.modules.workflow.dto.response.TaskReportResponse;
import com.sam.be.modules.workflow.dto.response.TimelineTaskResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CachedResponseRoundTripTest {

    private final ObjectMapper objectMapper = new AppConfig().objectMapper();

    @Test
    void readsProposalPageFromCachedJson() throws Exception {
        ProposalResponse proposal =
                ProposalResponse.builder()
                        .id(UUID.randomUUID())
                        .jobId(UUID.randomUUID())
                        .freelancerId(UUID.randomUUID())
                        .freelancerName("Freelancer")
                        .status(ProposalStatus.PENDING)
                        .createdAt(LocalDateTime.of(2026, 10, 13, 16, 56))
                        .build();
        PageResponse<ProposalResponse> page =
                new PageResponse<>(List.of(proposal), 0, 10, 1, 1, false);
        JavaType type =
                objectMapper
                        .getTypeFactory()
                        .constructParametricType(PageResponse.class, ProposalResponse.class);

        assertEquals(page, objectMapper.readValue(objectMapper.writeValueAsString(page), type));
    }

    @Test
    void readsOtherBuilderBasedCachedResponses() throws Exception {
        TaskReportResponse report =
                TaskReportResponse.builder()
                        .id(UUID.randomUUID())
                        .content("Đã hoàn thành")
                        .status(ProjectTaskStatus.SUBMITTED)
                        .createdAt(LocalDateTime.of(2026, 10, 13, 16, 56))
                        .build();
        TimelineTaskResponse task =
                TimelineTaskResponse.builder()
                        .id(UUID.randomUUID())
                        .status(ProjectTaskStatus.SUBMITTED)
                        .reports(List.of(report))
                        .build();
        List<Object> cachedValues =
                List.of(
                        ContractDetailResponse.builder()
                                .id(UUID.randomUUID())
                                .status(ContractStatus.COMPLETED)
                                .build(),
                        ContractRevisionResponse.builder()
                                .id(UUID.randomUUID())
                                .editorName("Client")
                                .build(),
                        ProjectDeliveryResponse.builder()
                                .id(UUID.randomUUID())
                                .status(DeliveryStatus.SUBMITTED)
                                .build(),
                        ProjectTimelineResponse.builder()
                                .jobId(UUID.randomUUID())
                                .tasks(List.of(task))
                                .build(),
                        AiRecommendationResponse.builder()
                                .id(UUID.randomUUID())
                                .status(RecommendationStatus.AUTO_MATCHED)
                                .skills(
                                        List.of(
                                                SkillExperienceDto.builder()
                                                        .skillName("Java")
                                                        .yearsOfExperience(3)
                                                        .build()))
                                .build());

        for (Object original : cachedValues) {
            Object restored =
                    objectMapper.readValue(
                            objectMapper.writeValueAsString(original), original.getClass());
            assertEquals(original, restored);
        }
    }
}
