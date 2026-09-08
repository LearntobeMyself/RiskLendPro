package org.example.risklendpro.cs;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cs")
public class CsStatusController {

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "service", "cs-service",
                "status", "UP",
                "llmConnected", false
        );
    }
}
