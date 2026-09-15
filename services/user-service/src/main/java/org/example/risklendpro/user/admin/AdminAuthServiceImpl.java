package org.example.risklendpro.user.admin;

import org.example.risklendpro.common.security.JwtConfig;
import org.example.risklendpro.user.entity.Admin;
import org.example.risklendpro.user.mapper.AdminMapper;
import org.example.risklendpro.user.admin.AdminRegisterRequest;
import org.example.risklendpro.user.admin.AdminLoginRequest;
import org.example.risklendpro.user.admin.AdminLoginResponse;
import org.example.risklendpro.user.admin.AdminAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import org.example.risklendpro.common.admin.AdminDateHelper;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {
    
    private final JwtConfig jwtConfig;
    private final PasswordEncoder passwordEncoder;
    private final AdminMapper adminMapper;
    
    @Autowired
    public AdminAuthServiceImpl(JwtConfig jwtConfig, PasswordEncoder passwordEncoder, AdminMapper adminMapper) {
        this.jwtConfig = jwtConfig;
        this.passwordEncoder = passwordEncoder;
        this.adminMapper = adminMapper;
    }
    
    @Override
    public void register(AdminRegisterRequest request) {
        // 1. 检查用户名是否已存在
        Admin existingAdmin = adminMapper.selectByUsername(request.getUsername());
        if (existingAdmin != null) {
            throw new RuntimeException("用户名已被注册");
        }
        
        // 2. 验证密码和确认密码是否一致
        if (!request.getPassword().equals(request.getRePassword())) {
            throw new RuntimeException("密码和确认密码不一致");
        }
        
        // 3. 创建管理员对象
        Admin admin = new Admin();
        admin.setUsername(request.getUsername());
        admin.setPassword(passwordEncoder.encode(request.getPassword()));
        admin.setPhoneNumber(request.getPhoneNumber());
        admin.setEmail(request.getEmail());
        admin.setCreateTime(new Date());
        admin.setUpdateTime(new Date());
        
        // 4. 保存管理员到数据库
        adminMapper.insert(admin);
        System.out.println("管理员注册成功: " + request.getUsername());
    }
    
    @Override
    public AdminLoginResponse login(AdminLoginRequest request) {
        // 1. 根据用户名查询管理员
        Admin admin = adminMapper.selectByUsername(request.getUsername());
        if (admin == null) {
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 2. 验证密码
        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 3. 生成JWT令牌（包含角色信息）
        String token = jwtConfig.generateToken(admin.getId().toString(), "ADMIN");
        
        // 4. 构建响应
        AdminLoginResponse response = new AdminLoginResponse();
        response.setId(admin.getId());
        response.setToken(token);
        
        return response;
    }

    @Override
    public Map<String, Object> getProfile(Long adminId) {
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new RuntimeException("管理员不存在");
        }
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", admin.getId());
        profile.put("username", admin.getUsername());
        profile.put("name", admin.getUsername());
        profile.put("phoneNumber", admin.getPhoneNumber());
        profile.put("email", admin.getEmail());
        profile.put("role", "SUPER_ADMIN");
        profile.put("avatar", "");
        profile.put("lastLoginTime", AdminDateHelper.formatDateTime(admin.getUpdateTime()));
        return profile;
    }
}