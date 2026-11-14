package org.example.risklendpro.risk.supplement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    /** EXIF 元数据风险提示（非视觉模型识别结果）。 */
    private MetadataRiskResult metadataRisk;

    private TamperCheckResult tamper;

    /** PASS / WARN / FAIL / SKIPPED；FAIL 表示材料应被拒绝保存。 */
    private String status;

    /** status == FAIL 时为 true，服务层据此拒绝落库。 */
    private boolean blocked;

    /** 对用户/管理端的口径说明，明确这是元数据检查而非内容识别。 */
    private String notice;

    private String detail;

    /**
     * 旧字段：历史上叫 aiGenerate。保留同名输出以兼容已发布的 App，
     * 语义已修正为"EXIF 元数据风险提示"。
     */
    @Deprecated
    @JsonProperty("aiGenerate")
    public MetadataRiskResult legacyAiGenerate() {
        return metadataRisk;
    }
}
