package org.example.risklendpro.loan.catalog.dto;

import lombok.Data;

@Data
public class TagSaveRequest {
    private Long tagId;
    private String tagName;
    private String categoryCode;
    private Integer sort;
}
