package org.example.risklendpro.loan.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.common.security.SecurityUtils;
import org.example.risklendpro.loan.catalog.dto.ImpressionRequest;
import org.example.risklendpro.loan.catalog.dto.ProductEventBatchRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "借贷产品目录（用户端）")
@RestController
@RequestMapping("/loan/products")
@PreAuthorize("hasRole('USER')")
public class UserLoanProductController {

    private final LoanCatalogService loanCatalogService;
    private final LoanRecommendationService loanRecommendationService;
    private final ProductEventService productEventService;

    public UserLoanProductController(LoanCatalogService loanCatalogService,
                                     LoanRecommendationService loanRecommendationService,
                                     ProductEventService productEventService) {
        this.loanCatalogService = loanCatalogService;
        this.loanRecommendationService = loanRecommendationService;
        this.productEventService = productEventService;
    }

    @Operation(summary = "U1 获取分类与筛选项")
    @GetMapping("/filters")
    public CommonResponse<Map<String, Object>> filters() {
        return CommonResponse.success("查询成功", loanCatalogService.filters());
    }

    @Operation(summary = "U2 产品列表")
    @GetMapping
    public CommonResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) List<String> institutionTypes,
            @RequestParam(required = false) Long amount,
            @RequestParam(required = false) BigDecimal maxRate,
            @RequestParam(required = false) Integer term,
            @RequestParam(required = false) String mortgageRequired,
            @RequestParam(required = false) String targetGroup,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return CommonResponse.success("查询成功", loanCatalogService.userList(
                keyword, categoryCode, institutionTypes, amount, maxRate, term,
                mortgageRequired, targetGroup, tagIds, sortBy, pageNum, pageSize));
    }

    @Operation(summary = "U6 产品对比")
    @GetMapping("/compare")
    public CommonResponse<Map<String, Object>> compare(@RequestParam List<Long> productIds) {
        Map<String, Object> data = loanCatalogService.compare(productIds);
        String hint = (String) data.get("hint");
        return CommonResponse.success(hint == null ? "查询成功" : hint, data);
    }

    @Operation(summary = "U4 机构详情")
    @GetMapping("/institutions/{institutionId}")
    public CommonResponse<Map<String, Object>> institution(@PathVariable Long institutionId) {
        return CommonResponse.success("查询成功", loanCatalogService.userInstitution(institutionId));
    }

    @Operation(summary = "U5 相似产品")
    @GetMapping("/{productId}/similar")
    public CommonResponse<List<Map<String, Object>>> similar(
            @PathVariable Long productId,
            @RequestParam(required = false) Integer size) {
        return CommonResponse.success("查询成功", loanCatalogService.similar(productId, size));
    }

    @Operation(summary = "上报获客行为事件，支持批量")
    @PostMapping("/events")
    public CommonResponse<Map<String, Object>> events(
            @RequestBody ProductEventBatchRequest batch, HttpServletRequest request) {
        Long userId = SecurityUtils.getUserIdFromRequest(request);
        return CommonResponse.success("上报成功", productEventService.ingest(userId, batch));
    }

    @Operation(summary = "确认推荐曝光，按 requestId 幂等")
    @PostMapping("/impressions")
    public CommonResponse<Map<String, Object>> impressions(
            @RequestBody ImpressionRequest impression, HttpServletRequest request) {
        Long userId = SecurityUtils.getUserIdFromRequest(request);
        return CommonResponse.success("记录成功", productEventService.recordImpressions(userId, impression));
    }

    @Operation(summary = "四场景获客推荐")
    @GetMapping("/recommendations")
    public CommonResponse<Map<String, Object>> recommendations(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        Long userId = SecurityUtils.getUserIdFromRequest(request);
        return CommonResponse.success("查询成功",
                loanRecommendationService.recommendations(userId, scene, productId, limit));
    }

    @Operation(summary = "U8 成功页再推荐")
    @GetMapping("/{productId}/post-loan-recommendations")
    public CommonResponse<Map<String, Object>> postLoanRecommendations(
            @PathVariable Long productId, HttpServletRequest request) {
        Long userId = SecurityUtils.getUserIdFromRequest(request);
        return CommonResponse.success("查询成功",
                loanRecommendationService.postLoanRecommendations(userId, productId));
    }

    @Operation(summary = "U3 产品详情")
    @GetMapping("/{productId}")
    public CommonResponse<Map<String, Object>> detail(@PathVariable Long productId) {
        return CommonResponse.success("查询成功", loanCatalogService.userDetail(productId));
    }
}
