package org.example.risklendpro.loan.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.common.security.SecurityUtils;
import org.example.risklendpro.loan.catalog.dto.ImagePresignRequest;
import org.example.risklendpro.loan.catalog.dto.InstitutionSaveRequest;
import org.example.risklendpro.loan.catalog.dto.ProductSaveRequest;
import org.example.risklendpro.loan.catalog.dto.ShelfRequest;
import org.example.risklendpro.loan.catalog.dto.TagSaveRequest;
import org.example.risklendpro.loan.catalog.vo.ImageVO;
import org.example.risklendpro.loan.catalog.vo.TagVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "借贷产品目录（管理端）")
@RestController
@RequestMapping("/admin/products")
@PreAuthorize("hasAuthority('sys:product')")
public class AdminLoanProductController {

    private final LoanCatalogService loanCatalogService;

    public AdminLoanProductController(LoanCatalogService loanCatalogService) {
        this.loanCatalogService = loanCatalogService;
    }

    @Operation(summary = "A1 获取上传凭证")
    @PreAuthorize("hasAuthority('sys:product:write')")
    @PostMapping("/images/presign")
    public CommonResponse<Map<String, Object>> presign(@RequestBody ImagePresignRequest request) {
        return CommonResponse.success("操作成功", loanCatalogService.presign(request, SecurityUtils.getAdminId()));
    }

    @Operation(summary = "A2 确认上传完成")
    @PreAuthorize("hasAuthority('sys:product:write')")
    @PostMapping("/images/{imageId}/confirm")
    public CommonResponse<ImageVO> confirm(@PathVariable Long imageId) {
        return CommonResponse.success("操作成功", loanCatalogService.confirmUpload(imageId, true));
    }

    @Operation(summary = "A9 获取补充条件模板")
    @GetMapping("/extra-templates")
    public CommonResponse<List<Map<String, Object>>> extraTemplates(@RequestParam String categoryCode) {
        return CommonResponse.success("查询成功", loanCatalogService.extraTemplates(categoryCode));
    }

    @Operation(summary = "A10 导出产品知识文本")
    @PreAuthorize("hasAuthority('sys:product:write')")
    @GetMapping("/knowledge/export")
    public CommonResponse<List<Map<String, Object>>> exportKnowledge(
            @RequestParam(required = false) List<Long> productIds,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime updatedSince,
            @RequestParam(required = false, defaultValue = "JSON") String format) {
        return CommonResponse.success("导出成功", loanCatalogService.exportKnowledge(productIds, updatedSince, format));
    }

    @Operation(summary = "A11 机构列表")
    @GetMapping("/institutions")
    public CommonResponse<Map<String, Object>> institutions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String institutionType,
            @RequestParam(required = false) Integer enabled,
            @RequestParam Integer pageNum,
            @RequestParam Integer pageSize) {
        return CommonResponse.success("查询成功",
                loanCatalogService.institutionList(keyword, institutionType, enabled, pageNum, pageSize));
    }

    @Operation(summary = "A12 新增机构")
    @PreAuthorize("hasAuthority('sys:product:write')")
    @PostMapping("/institutions")
    public CommonResponse<Map<String, Object>> createInstitution(@RequestBody InstitutionSaveRequest request) {
        return CommonResponse.success("新增成功", loanCatalogService.createInstitution(request, SecurityUtils.getAdminId()));
    }

    @Operation(summary = "A13 编辑机构")
    @PreAuthorize("hasAuthority('sys:product:write')")
    @PutMapping("/institutions/{institutionId}")
    public CommonResponse<Map<String, Object>> updateInstitution(
            @PathVariable Long institutionId,
            @RequestBody InstitutionSaveRequest request) {
        return CommonResponse.success("保存成功",
                loanCatalogService.updateInstitution(institutionId, request, SecurityUtils.getAdminId()));
    }

    @Operation(summary = "A14 启用/停用机构")
    @PreAuthorize("hasAuthority('sys:product:write')")
    @PostMapping("/institutions/{institutionId}/enabled")
    public CommonResponse<Map<String, Object>> enableInstitution(
            @PathVariable Long institutionId,
            @RequestParam boolean enabled) {
        return CommonResponse.success("操作成功",
                loanCatalogService.enableInstitution(institutionId, enabled, SecurityUtils.getAdminId()));
    }

    @Operation(summary = "A15 标签列表")
    @GetMapping("/tags")
    public CommonResponse<List<TagVO>> tags(@RequestParam(required = false) String categoryCode) {
        return CommonResponse.success("查询成功", loanCatalogService.tagList(categoryCode));
    }

    @Operation(summary = "A16 保存标签")
    @PreAuthorize("hasAuthority('sys:product:write')")
    @PostMapping("/tags")
    public CommonResponse<Map<String, Object>> saveTag(@RequestBody TagSaveRequest request) {
        return CommonResponse.success("保存成功", loanCatalogService.saveTag(request, SecurityUtils.getAdminId()));
    }

    @Operation(summary = "A17 删除标签")
    @PreAuthorize("hasAuthority('sys:product:write')")
    @DeleteMapping("/tags/{tagId}")
    public CommonResponse<Void> deleteTag(@PathVariable Long tagId) {
        loanCatalogService.deleteTag(tagId);
        return CommonResponse.success("删除成功", null);
    }

    @Operation(summary = "A4 管理端产品列表")
    @GetMapping
    public CommonResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) String status,
            @RequestParam Integer pageNum,
            @RequestParam Integer pageSize) {
        return CommonResponse.success("查询成功",
                loanCatalogService.adminList(keyword, categoryCode, institutionId, status, pageNum, pageSize));
    }

    @Operation(summary = "A3 管理端产品详情")
    @GetMapping("/{productId}")
    public CommonResponse<Map<String, Object>> detail(@PathVariable Long productId) {
        return CommonResponse.success("查询成功", loanCatalogService.adminDetail(productId));
    }

    @Operation(summary = "A5 新增产品")
    @PreAuthorize("hasAuthority('sys:product:write')")
    @PostMapping
    public CommonResponse<Map<String, Object>> create(@RequestBody ProductSaveRequest request) {
        return CommonResponse.success("新增成功", loanCatalogService.createProduct(request, SecurityUtils.getAdminId()));
    }

    @Operation(summary = "A6 编辑产品")
    @PreAuthorize("hasAuthority('sys:product:write')")
    @PutMapping("/{productId}")
    public CommonResponse<Map<String, Object>> update(
            @PathVariable Long productId,
            @RequestBody ProductSaveRequest request) {
        return CommonResponse.success("保存成功",
                loanCatalogService.updateProduct(productId, request, SecurityUtils.getAdminId()));
    }

    @Operation(summary = "A7 上架/下架")
    @PreAuthorize("hasAuthority('sys:product:write')")
    @PostMapping("/shelf")
    public CommonResponse<Map<String, Object>> shelf(@RequestBody ShelfRequest request) {
        return CommonResponse.success("处理完成", loanCatalogService.changeShelf(request, SecurityUtils.getAdminId()));
    }

    @Operation(summary = "A8 删除产品")
    @PreAuthorize("hasAuthority('sys:product:write')")
    @DeleteMapping("/{productId}")
    public CommonResponse<Void> delete(@PathVariable Long productId) {
        loanCatalogService.deleteProduct(productId, SecurityUtils.getAdminId());
        return CommonResponse.success("删除成功", null);
    }
}
