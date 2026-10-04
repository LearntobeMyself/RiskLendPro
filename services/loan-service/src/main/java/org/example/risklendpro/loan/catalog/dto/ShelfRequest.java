package org.example.risklendpro.loan.catalog.dto;

import lombok.Data;

import java.util.List;

@Data
public class ShelfRequest {
    private List<Long> productIds;
    private String targetStatus;
}
