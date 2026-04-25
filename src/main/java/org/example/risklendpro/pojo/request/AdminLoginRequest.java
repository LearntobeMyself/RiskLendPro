package org.example.risklendpro.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理员登录请求")
public class AdminLoginRequest {
    @Schema(description = "管理员昵称", example = "admin")
    private String username;
    @Schema(description = "管理员密码", example = "123456")
    private String password;
}