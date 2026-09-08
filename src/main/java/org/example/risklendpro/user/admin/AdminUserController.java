package org.example.risklendpro.user.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.user.admin.AdminUserCreateRequest;
import org.example.risklendpro.user.admin.AdminUserStatusRequest;
import org.example.risklendpro.user.admin.AdminUserUpdateRequest;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.user.admin.AdminUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "用户管理", description = "管理员用户 CRUD")
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private AdminUserService adminUserService;

    @Operation(summary = "用户列表")
    @GetMapping
    public CommonResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String accountStatus,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String phoneNumber) {
        String resolvedStatus = accountStatus != null && !accountStatus.isBlank() ? accountStatus : status;
        String resolvedName = realName != null && !realName.isBlank() ? realName : name;
        String resolvedPhone = phoneNumber != null && !phoneNumber.isBlank() ? phoneNumber : phone;
        return CommonResponse.success("查询成功",
                adminUserService.listUsers(page, size, resolvedStatus, resolvedName, resolvedPhone));
    }

    @Operation(summary = "用户导出")
    @GetMapping("/export")
    public CommonResponse<Map<String, Object>> export(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String accountStatus,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String phoneNumber) {
        String resolvedStatus = accountStatus != null && !accountStatus.isBlank() ? accountStatus : status;
        String resolvedName = realName != null && !realName.isBlank() ? realName : name;
        String resolvedPhone = phoneNumber != null && !phoneNumber.isBlank() ? phoneNumber : phone;
        return CommonResponse.success("导出成功",
                adminUserService.exportUsers(resolvedStatus, resolvedName, resolvedPhone));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{userId}")
    public CommonResponse<Map<String, Object>> detail(@PathVariable Long userId) {
        return CommonResponse.success("查询成功", adminUserService.getUserDetail(userId));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    public CommonResponse<Map<String, Object>> create(@RequestBody AdminUserCreateRequest request) {
        return CommonResponse.success("创建成功", adminUserService.createUser(request));
    }

    @Operation(summary = "编辑用户")
    @PutMapping("/{userId}")
    public CommonResponse<Map<String, Object>> update(
            @PathVariable Long userId, @RequestBody AdminUserUpdateRequest request) {
        return CommonResponse.success("更新成功", adminUserService.updateUser(userId, request));
    }

    @Operation(summary = "更新用户状态")
    @PatchMapping("/{userId}/status")
    public CommonResponse<Map<String, Object>> updateStatus(
            @PathVariable Long userId, @RequestBody AdminUserStatusRequest request) {
        return CommonResponse.success("状态更新成功", adminUserService.updateUserStatus(userId, request));
    }
}
