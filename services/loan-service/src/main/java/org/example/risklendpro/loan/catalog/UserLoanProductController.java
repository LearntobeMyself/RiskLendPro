package org.example.risklendpro.loan.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.common.api.CommonResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "借贷产品目录（用户端）")
@RestController
@RequestMapping("/loan/products")
@PreAuthorize("hasRole('USER')")
public class UserLoanProductController {

    private final LoanCatalogService loanCatalogService;

    public UserLoanProductController(LoanCatalogService loanCatalogService) {
        this.loanCatalogService = loanCatalogService;
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
            @RequestParam Integer pageNum,
            @RequestParam Integer pageSize) {
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

    @Operation(summary = "U3 产品详情")
    @GetMapping("/{productId}")
    public CommonResponse<Map<String, Object>> detail(@PathVariable Long productId) {
        return CommonResponse.success("查询成功", loanCatalogService.userDetail(productId));
    }
}
