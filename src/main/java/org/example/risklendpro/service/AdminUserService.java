package org.example.risklendpro.service;

import org.example.risklendpro.pojo.request.AdminUserCreateRequest;
import org.example.risklendpro.pojo.request.AdminUserStatusRequest;
import org.example.risklendpro.pojo.request.AdminUserUpdateRequest;

import java.util.Map;

public interface AdminUserService {

    Map<String, Object> listUsers(Integer page, Integer size, String status, String name, String phone);

    Map<String, Object> getUserDetail(Long userId);

    Map<String, Object> createUser(AdminUserCreateRequest request);

    Map<String, Object> updateUser(Long userId, AdminUserUpdateRequest request);

    Map<String, Object> updateUserStatus(Long userId, AdminUserStatusRequest request);

    Map<String, Object> exportUsers(String status, String name, String phone);
}
