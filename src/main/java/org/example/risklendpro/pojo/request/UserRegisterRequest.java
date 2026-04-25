package org.example.risklendpro.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户注册请求")
public class UserRegisterRequest {
    @Schema(description = "用户姓名", example = "张三")
    private String realName;
    @Schema(description = "用户手机号", example = "13800138001")
    private String phoneNumber;
    @Schema(description = "用户邮箱", example = "zhangsan@example.com")
    private String email;
    @Schema(description = "用户身份证号", example = "110101199001011234")
    private String idCard;
    @Schema(description = "用户密码", example = "123456")
    private String password;
    @Schema(description = "用户重新输入密码", example = "123456")
    private String repassword;
}