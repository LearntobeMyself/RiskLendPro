package org.example.risklendpro.loan.catalog.dto;

import lombok.Data;

@Data
public class ImagePresignRequest {
    private String imageType;
    private String fileName;
    private String contentType;
    private Long fileSize;
}
