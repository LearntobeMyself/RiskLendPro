package org.example.risklendpro.loan.catalog.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ProductEventRequest {
    private Long productId;
    private String eventType;
    private String scene;
    private String sessionId;
    private String keyword;
    private Map<String, Object> metadata;
}
