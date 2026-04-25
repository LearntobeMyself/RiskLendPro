package org.example.risklendpro.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.pojo.request.AdminRegisterRequest;
import org.example.risklendpro.pojo.request.AdminLoginRequest;
import org.example.risklendpro.pojo.response.CommonResponse;
import org.example.risklendpro.pojo.response.AdminLoginResponse;
import org.example.risklendpro.service.AdminAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}