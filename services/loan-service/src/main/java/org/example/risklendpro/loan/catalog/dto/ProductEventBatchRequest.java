package org.example.risklendpro.loan.catalog.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProductEventBatchRequest {
    private List<ProductEventRequest> events;
}
