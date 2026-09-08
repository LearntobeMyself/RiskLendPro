package org.example.risklendpro.user.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.user.admin.AdminRegisterRequest;
import org.example.risklendpro.user.admin.AdminLoginRequest;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.user.admin.AdminLoginResponse;
import org.example.risklendpro.user.admin.AdminAuthService;
import org.example.risklendpro.common.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "管理员认证模块", description = "管理员注册登录相关接口")
@RestController
@RequestMapping("/admin")
public class AdminAuthController {
    
    private final AdminAuthService adminAuthService;
    
    @Autowired
    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }
    
    @Operation(summary = "管理员注册", description = "管理员注册接口")
    @PostMapping("/register")
    public CommonResponse<Void> register(@RequestBody AdminRegisterRequest request) {
        adminAuthService.register(request);
        return CommonResponse.success("注册成功", null);
    }
    
    @Operation(summary = "管理员登录", description = "管理员登录接口")
    @PostMapping("/login")
    public CommonResponse<AdminLoginResponse> login(@RequestBody AdminLoginRequest request) {
        AdminLoginResponse response = adminAuthService.login(request);
        return CommonResponse.success("登录成功", response);
    }

    @Operation(summary = "当前管理员资料", description = "获取当前登录管理员信息")
    @GetMapping("/profile")
    public CommonResponse<Map<String, Object>> profile() {
        Map<String, Object> data = adminAuthService.getProfile(SecurityUtils.getAdminId());
        return CommonResponse.success("查询成功", data);
    }
}