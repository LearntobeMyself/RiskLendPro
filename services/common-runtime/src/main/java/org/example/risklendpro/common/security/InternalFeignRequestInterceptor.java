package org.example.risklendpro.common.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

@Component
public class InternalFeignRequestInterceptor implements RequestInterceptor {

    private final InternalApiProperties internalApiProperties;

    public InternalFeignRequestInterceptor(InternalApiProperties internalApiProperties) {
        this.internalApiProperties = internalApiProperties;
    }

    @Override
    public void apply(RequestTemplate template) {
        String path = template.path();
        String url = template.url();
        if (!containsInternal(path) && !containsInternal(url)) {
            return;
        }
        String token = internalApiProperties.getApiToken();
        if (token == null || token.isBlank()) {
            return;
        }
        template.header("Authorization", "Bearer " + token);
    }

    private static boolean containsInternal(String value) {
        return value != null && value.contains("/internal/");
    }
}
