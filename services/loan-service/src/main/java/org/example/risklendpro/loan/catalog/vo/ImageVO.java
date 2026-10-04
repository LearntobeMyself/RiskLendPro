package org.example.risklendpro.loan.catalog.vo;

import lombok.Data;

@Data
public class ImageVO {
    private Long imageId;
    private String imageType;
    private String url;
    private Integer sort;
    private String objectKey;
}
