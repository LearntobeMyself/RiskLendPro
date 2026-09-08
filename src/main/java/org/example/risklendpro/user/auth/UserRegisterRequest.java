package org.example.risklendpro.user.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户注册请求")
public class UserRegisterRequest {
    @Schema(description = "用户姓名", example = "陈优质")
    private String realName;
    @Schema(description = "用户手机号", example = "13800148001")
    private String phoneNumber;
    @Schema(description = "用户邮箱", example = "chen_a@example.com")
    private String email;
    @Schema(description = "用户身份证号（与 user_external_features.id_card 及风控申请一致）", example = "110101198503151001")
    private String idCard;
    @Schema(description = "用户密码", example = "Test123456")
    private String password;
    @Schema(description = "用户重新输入密码", example = "Test123456")
    private String repassword;
}
