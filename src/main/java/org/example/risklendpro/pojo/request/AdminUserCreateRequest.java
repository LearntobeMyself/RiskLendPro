package org.example.risklendpro.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理员创建用户请求")
public class AdminUserCreateRequest {
    private String name;
    private String phone;
    private String email;
    private String idCard;
    private String status;
}
