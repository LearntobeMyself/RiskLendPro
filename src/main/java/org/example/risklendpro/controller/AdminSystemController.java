package org.example.risklendpro.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.pojo.request.AdminCreateRequest;
import org.example.risklendpro.pojo.request.AdminUpdateRequest;
import org.example.risklendpro.pojo.request.SystemConfigUpdateRequest;
import org.example.risklendpro.pojo.response.CommonResponse;
import org.example.risklendpro.service.AdminSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "系统管理", description = "管理员、日志、配置与备份")
@RestController
@RequestMapping("/admin/system")
public class AdminSystemController {

    @Autowired
    private AdminSystemService adminSystemService;

    @Operation(summary = "管理员列表")
    @GetMapping("/admins")
    public CommonResponse<Map<String, Object>> listAdmins(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return CommonResponse.success("查询成功", adminSystemService.listAdmins(page, size));
    }

    @Operation(summary = "新增管理员")
    @PostMapping("/admins")
    public CommonResponse<Map<String, Object>> createAdmin(@RequestBody AdminCreateRequest request) {
        return CommonResponse.success("创建成功", adminSystemService.createAdmin(request));
    }

    @Operation(summary = "更新管理员")
    @PutMapping("/admins/{adminId}")
    public CommonResponse<Map<String, Object>> updateAdmin(
            @PathVariable Long adminId, @RequestBody AdminUpdateRequest request) {
        return CommonResponse.success("更新成功", adminSystemService.updateAdmin(adminId, request));
    }

    @Operation(summary = "删除管理员")
    @DeleteMapping("/admins/{adminId}")
    public CommonResponse<Void> deleteAdmin(@PathVariable Long adminId) {
        adminSystemService.deleteAdmin(adminId);
        return CommonResponse.success("删除成功", null);
    }

    @Operation(summary = "操作日志")
    @GetMapping("/operation-logs")
    public CommonResponse<Map<String, Object>> operationLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return CommonResponse.success("查询成功",
                adminSystemService.listOperationLogs(page, size, module, startDate, endDate));
    }

    @Operation(summary = "系统配置")
    @GetMapping("/config")
    public CommonResponse<Map<String, Object>> getConfig() {
        return CommonResponse.success("查询成功", adminSystemService.getConfig());
    }

    @Operation(summary = "更新系统配置")
    @PutMapping("/config")
    public CommonResponse<Map<String, Object>> updateConfig(@RequestBody SystemConfigUpdateRequest request) {
        return CommonResponse.success("更新成功", adminSystemService.updateConfig(request));
    }

    @Operation(summary = "备份列表")
    @GetMapping("/backups")
    public CommonResponse<Map<String, Object>> listBackups(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return CommonResponse.success("查询成功", adminSystemService.listBackups(page, size));
    }

    @Operation(summary = "创建备份")
    @PostMapping("/backups")
    public CommonResponse<Map<String, Object>> createBackup() {
        return CommonResponse.success("备份创建成功", adminSystemService.createBackup());
    }

    @Operation(summary = "恢复备份")
    @PostMapping("/backups/{backupId}/restore")
    public CommonResponse<Map<String, Object>> restoreBackup(@PathVariable String backupId) {
        return CommonResponse.success("恢复成功", adminSystemService.restoreBackup(backupId));
    }

    @Operation(summary = "下载备份")
    @GetMapping("/backups/{backupId}/download")
    public ResponseEntity<byte[]> downloadBackup(@PathVariable String backupId) {
        byte[] content = adminSystemService.downloadBackup(backupId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + backupId + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }
}
