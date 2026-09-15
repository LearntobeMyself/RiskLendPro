package org.example.risklendpro.risk.supplement;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "risk.supplement")
public class SupplementMaterialProperties {
    private String uploadDir = "upload/risk-supplement";
    private int retentionDays = 30;
    private int maxFileSizeMb = 5;
    private String allowedExtensions = "jpg,jpeg,png,pdf";

    public List<String> allowedExtensionList() {
        return Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public long maxFileSizeBytes() {
        return maxFileSizeMb * 1024L * 1024L;
    }
}
