package org.example.risklendpro.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户登录请求")
public class UserLoginRequest {
    @Schema(description = "用户手机号", example = "13800138001")
    private String phoneNumber;
    @Schema(description = "用户密码", example = "123456")
    private String password;
}