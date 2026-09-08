package org.example.risklendpro.user.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户登录响应")
public class UserLoginResponse {
    @Schema(description = "用户ID")
    private Long id;
    @Schema(description = "JWT令牌")
    private String token;
    @Schema(description = "用户角色")
    private String role;
    @Schema(description = "评估状态：NOT_ASSESSED=未评估, ASSESSING=评估中, APPROVED=已获额度")
    private String assessmentStatus;
}