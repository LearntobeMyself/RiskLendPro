package org.example.risklendpro.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "系统管理员更新请求")
public class AdminUpdateRequest {
    private String username;
    private String password;
    private String phoneNumber;
    private String email;
}
