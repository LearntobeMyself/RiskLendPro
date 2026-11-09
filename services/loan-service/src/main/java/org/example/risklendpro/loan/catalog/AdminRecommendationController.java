package org.example.risklendpro.loan.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.common.security.SecurityUtils;
import org.example.risklendpro.loan.catalog.dto.RecommendationConfigRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "获客推荐（管理端）")
@RestController
@RequestMapping({"/admin/products/recommendation", "/admin/loan/recommendation"})
@PreAuthorize("hasAuthority('sys:product')")
public class AdminRecommendationController {

    private final RecommendationMetricsService metricsService;
    private final RecommendationConfigService configService;

    public AdminRecommendationController(RecommendationMetricsService metricsService,
                                         RecommendationConfigService configService) {
        this.metricsService = metricsService;
        this.configService = configService;
    }

    @Operation(summary = "获客推荐漏斗指标")
    @GetMapping("/metrics")
    public CommonResponse<Map<String, Object>> metrics(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false, defaultValue = "7") Integer days) {
        return CommonResponse.success("查询成功", metricsService.metrics(scene, days));
    }

    @Operation(summary = "获取推荐场景配置")
    @GetMapping("/config")
    public CommonResponse<Map<String, Object>> config(@RequestParam(defaultValue = "HOME") String scene) {
        RecommendationConfigService.SceneConfig config = configService.get(RecommendationScene.parse(scene));
        return CommonResponse.success("查询成功", Map.of(
                "scene", config.scene().name(),
                "enabled", config.enabled(),
                "version", config.version(),
                "config", config.values()));
    }

    @Operation(summary = "保存推荐场景配置")
    @PutMapping("/config")
    public CommonResponse<Map<String, Object>> updateConfig(
            @RequestParam(defaultValue = "HOME") String scene,
            @RequestBody RecommendationConfigRequest request) {
        return CommonResponse.success("保存成功",
                configService.update(RecommendationScene.parse(scene), request, SecurityUtils.getAdminId()));
    }
}

