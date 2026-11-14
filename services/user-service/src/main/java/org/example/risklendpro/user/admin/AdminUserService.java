package org.example.risklendpro.user.admin;

import org.example.risklendpro.user.admin.AdminUserCreateRequest;
import org.example.risklendpro.user.admin.AdminUserStatusRequest;
import org.example.risklendpro.user.admin.AdminUserUpdateRequest;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface AdminUserService {

    Map<String, Object> listUsers(Integer page, Integer size, String status, String name, String phone);

    Map<String, Object> getUserDetail(Long userId);

    Map<String, Object> createUser(AdminUserCreateRequest request);

    Map<String, Object> updateUser(Long userId, AdminUserUpdateRequest request);

    Map<String, Object> updateUserStatus(Long userId, AdminUserStatusRequest request);

    /** 直接返回 CSV 文件流，不再返回二次下载地址。 */
    ResponseEntity<byte[]> exportUsers(String status, String name, String phone);
}
