package org.example.risklendpro.user.auth;

import org.example.risklendpro.common.security.JwtConfig;
import org.example.risklendpro.user.entity.User;
import org.example.risklendpro.user.mapper.UserMapper;
import org.example.risklendpro.user.auth.UserRegisterRequest;
import org.example.risklendpro.user.auth.UserLoginRequest;
import org.example.risklendpro.user.auth.UserLoginResponse;
import org.example.risklendpro.user.auth.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class AuthServiceImpl implements AuthService {
    
    private final JwtConfig jwtConfig;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    
    @Autowired
    public AuthServiceImpl(JwtConfig jwtConfig, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.jwtConfig = jwtConfig;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }
    
    @Override
    public void register(UserRegisterRequest request) {
        // 1. 检查手机号是否已存在
        User existingUser = userMapper.selectByPhoneNumber(request.getPhoneNumber());
        if (existingUser != null) {
            throw new RuntimeException("手机号已被注册");
        }
        
        // 2. 验证密码和确认密码是否一致
        if (!request.getPassword().equals(request.getRepassword())) {
            throw new RuntimeException("密码和确认密码不一致");
        }
        
        // 3. 创建用户对象
        User user = new User();
        user.setRealName(request.getRealName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setEmail(request.getEmail());
        user.setIdCard(request.getIdCard());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        user.setAssessmentStatus("NOT_ASSESSED");
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());
        
        // 4. 保存用户到数据库
        userMapper.insert(user);
        System.out.println("用户注册成功: " + request.getPhoneNumber());
    }
    
    @Override
    public UserLoginResponse login(UserLoginRequest request) {
        // 1. 根据手机号查询用户
        User user = userMapper.selectByPhoneNumber(request.getPhoneNumber());
        if (user == null) {
            throw new RuntimeException("手机号或密码错误");
        }
        
        // 2. 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("手机号或密码错误");
        }
        
        // 3. 生成JWT令牌（包含角色信息）
        String token = jwtConfig.generateToken(user.getId().toString(), user.getRole());
        
        // 4. 构建响应
        UserLoginResponse response = new UserLoginResponse();
        response.setId(user.getId());
        response.setToken(token);
        response.setRole(user.getRole());
        response.setAssessmentStatus(user.getAssessmentStatus());
        
        return response;
    }
}