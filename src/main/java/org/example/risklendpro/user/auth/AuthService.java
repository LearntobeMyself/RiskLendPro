package org.example.risklendpro.user.auth;

import org.example.risklendpro.user.auth.UserRegisterRequest;
import org.example.risklendpro.user.auth.UserLoginRequest;
import org.example.risklendpro.user.auth.UserLoginResponse;

public interface AuthService {
    /**
     * 用户注册
     * @param request 注册信息
     */
    void register(UserRegisterRequest request);
    
    /**
     * 用户登录
     * @param request 登录信息
     * @return 登录响应，包含token和用户信息
     */
    UserLoginResponse login(UserLoginRequest request);
}