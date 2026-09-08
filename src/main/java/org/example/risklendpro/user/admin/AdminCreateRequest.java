package org.example.risklendpro.user.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "系统管理员创建请求")
public class AdminCreateRequest {
    private String username;
    private String password;
    private String phoneNumber;
    private String email;
}
