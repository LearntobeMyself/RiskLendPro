package org.example.risklendpro.cs;

import org.example.risklendpro.cs.config.CsLlmProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cs")
public class CsStatusController {

    private final CsLlmProperties llmProperties;

    public CsStatusController(CsLlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean llmConfigured = StringUtils.hasText(llmProperties.getApiKey());
        return Map.of(
                "service", "cs-service",
                "status", "UP",
                "llmConnected", llmConfigured
        );
    }
}
