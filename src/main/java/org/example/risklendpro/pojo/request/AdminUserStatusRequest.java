package org.example.risklendpro.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户启用/禁用/冻结")
public class AdminUserStatusRequest {
    @Schema(description = "ACTIVE / DISABLED / FROZEN")
    private String status;
    @Schema(description = "操作原因")
    private String reason;
    @Schema(description = "兼容旧字段：true=ACTIVE, false=DISABLED")
    private Boolean enabled;
}
