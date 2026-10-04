package org.example.risklendpro.loan.catalog.vo;

import lombok.Data;

@Data
public class TagVO {
    private Long tagId;
    private String tagName;
    private String categoryCode;
    private Integer productCount;
}
