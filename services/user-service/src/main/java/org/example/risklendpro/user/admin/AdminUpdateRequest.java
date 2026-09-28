package org.example.risklendpro.user.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "系统管理员更新请求")
public class AdminUpdateRequest {
    private String username;
    private String password;
    private String phoneNumber;
    private String email;
    @Schema(description = "角色码 RISK_MANAGER/COLLECTOR/AUDITOR/CS_AGENT/SYS_ADMIN")
    private String role;
}
