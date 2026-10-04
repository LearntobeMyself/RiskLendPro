package org.example.risklendpro.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "security.internal")
public class InternalApiProperties {
    /**
     * 服务间调用 /internal/** 的共享 token，通过 INTERNAL_API_TOKEN 注入。
     */
    private String apiToken = "";
}
