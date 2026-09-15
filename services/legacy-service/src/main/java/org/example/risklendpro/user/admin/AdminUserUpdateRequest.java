package org.example.risklendpro.user.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理员更新用户请求")
public class AdminUserUpdateRequest {
    private String name;
    private String phone;
    private String email;
    private String idCard;
    private String status;
}
