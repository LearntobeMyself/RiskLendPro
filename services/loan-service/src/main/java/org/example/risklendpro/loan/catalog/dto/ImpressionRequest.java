package org.example.risklendpro.loan.catalog.dto;

import lombok.Data;

import java.util.List;

@Data
public class ImpressionRequest {
    private String requestId;
    private String scene;
    private String sessionId;
    private String source;
    private String abGroup;
    private String modelVersion;
    private List<Long> productIds;
}
