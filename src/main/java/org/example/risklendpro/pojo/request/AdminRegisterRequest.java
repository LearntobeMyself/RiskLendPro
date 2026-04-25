package org.example.risklendpro.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理员注册请求")
public class AdminRegisterRequest {
    @Schema(description = "昵称", example = "admin")
    private String username;
    @Schema(description = "密码", example = "123456")
    private String password;
    @Schema(description = "二次密码", example = "123456")
    private String rePassword;
    @Schema(description = "手机号", example = "13800138001")
    private String phoneNumber;
    @Schema(description = "用户邮箱", example = "admin@example.com")
    private String email;
}