package org.example.risklendpro.service;

import org.example.risklendpro.pojo.request.AdminRegisterRequest;
import org.example.risklendpro.pojo.request.AdminLoginRequest;
import org.example.risklendpro.pojo.response.AdminLoginResponse;

import java.util.Map;

public interface AdminAuthService {
    /**
     * 管理员注册
     * @param request 注册信息
     */
    void register(AdminRegisterRequest request);
    
    /**
     * 管理员登录
     * @param request 登录信息
     * @return 登录响应，包含token
     */
    AdminLoginResponse login(AdminLoginRequest request);

    /**
     * 当前管理员资料
     */
    Map<String, Object> getProfile(Long adminId);
}