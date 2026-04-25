package org.example.risklendpro.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.pojo.request.UserRegisterRequest;
import org.example.risklendpro.pojo.request.UserLoginRequest;
import org.example.risklendpro.pojo.response.CommonResponse;
import org.example.risklendpro.pojo.response.UserLoginResponse;
import org.example.risklendpro.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证模块", description = "用户注册登录相关接口")
@RestController
@RequestMapping("/auth")
public class AuthController {
    
    private final AuthService authService;
    
    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    
    @Operation(summary = "用户注册", description = "用户注册接口")
    @PostMapping("/register")
    public CommonResponse<Void> register(@RequestBody UserRegisterRequest request) {
        authService.register(request);
        return CommonResponse.success("注册成功", null);
    }
    
    @Operation(summary = "用户登录", description = "用户登录接口，登录后返回token和role，如果是用户还返回当前评估状态")
    @PostMapping("/login")
    public CommonResponse<UserLoginResponse> login(@RequestBody UserLoginRequest request) {
        UserLoginResponse response = authService.login(request);
        return CommonResponse.success("登录成功", response);
    }
}