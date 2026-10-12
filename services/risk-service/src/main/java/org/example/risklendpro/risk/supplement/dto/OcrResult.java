package org.example.risklendpro.risk.supplement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrResult {
    private boolean success;
    private String text;
    private String error;
}
