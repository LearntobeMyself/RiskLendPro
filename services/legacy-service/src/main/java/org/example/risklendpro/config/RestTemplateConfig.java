package org.example.risklendpro.config;

import org.example.risklendpro.risk.blacklist.CozeWorkflowProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate cozeRestTemplate(RestTemplateBuilder builder, CozeWorkflowProperties properties) {
        return builder
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .build();
    }
}
