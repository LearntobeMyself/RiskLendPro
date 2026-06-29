package org.example.risklendpro.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "系统配置更新请求")
public class SystemConfigUpdateRequest {
    private Boolean emailEnabled;
    private Integer logRetentionDays;
    private Integer backupRetentionDays;
    private String systemName;
}
