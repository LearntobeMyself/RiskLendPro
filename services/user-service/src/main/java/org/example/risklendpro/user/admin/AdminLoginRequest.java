package org.example.risklendpro.user.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "管理员登录请求")
public class AdminLoginRequest {
    @NotBlank(message = "用户名不能为空")
    @Schema(description = "管理员昵称", example = "admin")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Schema(description = "管理员密码", example = "123456")
    private String password;
}