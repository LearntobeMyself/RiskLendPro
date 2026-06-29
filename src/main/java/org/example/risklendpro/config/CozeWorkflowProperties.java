package org.example.risklendpro.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "risk.coze")
public class CozeWorkflowProperties {

    private boolean enabled = false;
    private String apiUrl = "https://api.coze.cn/v1/workflow/run";
    private String token = "";
    private String workflowId = "";
    private String workflowVersion = "";
    private String cron = "0 0 2 * * ?";
    private int readTimeoutMs = 300_000;
    private int connectTimeoutMs = 10_000;
}
