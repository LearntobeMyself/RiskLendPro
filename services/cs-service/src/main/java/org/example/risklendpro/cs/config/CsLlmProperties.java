package org.example.risklendpro.cs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型接入配置。api-key 不写进仓库，请在 config/local/application-local.yml 覆盖 cs.llm.api-key。
 */
@Data
@Component
@ConfigurationProperties(prefix = "cs.llm")
public class CsLlmProperties {
    /** OpenAI 兼容接口 baseUrl，默认 DeepSeek。 */
    private String baseUrl = "https://api.deepseek.com";
    /** 必填，否则调用时抛错。 */
    private String apiKey = "";
    private String model = "deepseek-chat";
    private Integer maxTokens = 1024;
    private Double temperature = 0.3;
    private Integer timeoutSeconds = 60;
}
