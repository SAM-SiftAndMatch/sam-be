package com.sam.be.modules.subscription.controller;

import com.sam.be.common.constant.enums.UserRole;
import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.subscription.repository.ServicePackageRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/packages")
public class PackageCatalogController {
    private final ServicePackageRepository packages;

    public record Plan(
            UUID id, String name, String type, java.math.BigDecimal price, String description) {}

    @GetMapping
    public ApiResponse<List<Plan>> list() {
        String suffix =
                SecurityUtils.hasRole(UserRole.CLIENT.name()) ? "000000000005" : "000000000006";
        var plans =
                packages.findAll().stream()
                        .filter(
                                p ->
                                        p.getId().toString().endsWith(suffix)
                                                && Boolean.TRUE.equals(p.getIsActive())
                                                && !Boolean.TRUE.equals(p.getIsDynamicPrice())
                                                && p.getPrice() != null
                                                && p.getPrice().signum() > 0)
                        .map(
                                p ->
                                        new Plan(
                                                p.getId(),
                                                p.getName(),
                                                p.getType().name(),
                                                p.getPrice(),
                                                p.getDescription()))
                        .toList();
        return ApiResponse.<List<Plan>>builder().result(plans).build();
    }
}
