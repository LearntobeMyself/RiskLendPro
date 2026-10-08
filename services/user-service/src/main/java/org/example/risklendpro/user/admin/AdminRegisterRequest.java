package org.example.risklendpro.user.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "管理员注册请求")
public class AdminRegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Schema(description = "昵称", example = "admin")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "123456")
    private String password;
    @NotBlank(message = "确认密码不能为空")
    @Schema(description = "二次密码", example = "123456")
    private String rePassword;
    @Schema(description = "手机号", example = "13800138001")
    private String phoneNumber;
    @Schema(description = "用户邮箱", example = "admin@example.com")
    private String email;
}