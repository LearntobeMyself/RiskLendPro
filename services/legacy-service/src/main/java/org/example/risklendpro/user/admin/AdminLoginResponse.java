package org.example.risklendpro.user.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理员登录响应")
public class AdminLoginResponse {
    @Schema(description = "管理员ID")
    private Long id;
    @Schema(description = "JWT令牌")
    private String token;
}