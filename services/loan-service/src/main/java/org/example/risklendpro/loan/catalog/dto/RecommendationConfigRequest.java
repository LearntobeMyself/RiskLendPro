package org.example.risklendpro.loan.catalog.dto;

import lombok.Data;

import java.util.Map;

@Data
public class RecommendationConfigRequest {
    private Map<String, Object> config;
    private Boolean enabled;
}
