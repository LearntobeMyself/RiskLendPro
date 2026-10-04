package org.example.risklendpro.loan.catalog.dto;

import lombok.Data;

@Data
public class InstitutionSaveRequest {
    private String institutionName;
    private String institutionType;
    private Long logoImageId;
    private String introduction;
    private Integer version;
}
