package org.example.risklendpro.storage;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

public record PresignResult(
        String uploadUrl,
        String method,
        Map<String, String> headers,
        LocalDateTime expireAt
) {
    public static PresignResult put(String uploadUrl, String contentType, Duration expire) {
        return new PresignResult(
                uploadUrl,
                "PUT",
                Map.of("Content-Type", contentType),
                LocalDateTime.now().plus(expire)
        );
    }
}
