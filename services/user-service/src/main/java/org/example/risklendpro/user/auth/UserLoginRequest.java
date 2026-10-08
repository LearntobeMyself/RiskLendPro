package org.example.risklendpro.user.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "用户登录请求")
public class UserLoginRequest {
    @NotBlank(message = "手机号不能为空")
    @Schema(description = "用户手机号", example = "13800138001")
    private String phoneNumber;
    @NotBlank(message = "密码不能为空")
    @Schema(description = "用户密码", example = "123456")
    private String password;
}