package com.sam.be.modules.skill.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.modules.skill.dto.response.SkillResponse;
import com.sam.be.modules.skill.entity.Skill;
import com.sam.be.modules.skill.repository.SkillRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
@Tag(name = "Skill Management", description = "APIs for querying available skills")
public class SkillController {

    private final SkillRepository skillRepository;

    @GetMapping
    @Operation(
            summary = "Get all skills",
            description = "Retrieve list of all skills with optional keyword search")
    public ApiResponse<List<SkillResponse>> getSkills(
            @RequestParam(required = false) String search) {
        List<Skill> skills;
        if (search != null && !search.isBlank()) {
            skills = skillRepository.findByNameContainingIgnoreCase(search.trim());
        } else {
            skills = skillRepository.findAll();
        }

        List<SkillResponse> response =
                skills.stream()
                        .map(s -> SkillResponse.builder().id(s.getId()).name(s.getName()).build())
                        .toList();

        return ApiResponse.<List<SkillResponse>>builder().result(response).build();
    }
}
