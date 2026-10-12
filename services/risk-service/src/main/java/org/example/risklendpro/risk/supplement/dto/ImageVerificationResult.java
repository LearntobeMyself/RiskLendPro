package org.example.risklendpro.risk.supplement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageVerificationResult {
    private String supplementId;
    private OcrResult ocr;
    private AiGenerateCheckResult aiGenerate;
    private TamperCheckResult tamper;
    private String status;
    private String detail;
}
