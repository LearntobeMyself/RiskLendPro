package org.example.risklendpro.risk.supplement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片元数据（EXIF）风险提示结果。
 *
 * <p>说明：本结果完全来自 EXIF 元数据规则判断，<b>不包含任何像素级视觉模型分析</b>，
 * 因此 {@link #riskScore} 表示"元数据风险分"，不能解释为"AI 生成概率"。
 * 改写或擦除 EXIF 即可绕过这些规则，所以它只用于风险提示与工单留痕，
 * 不能作为"该图片是否由 AI 生成"的结论。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataRiskResult {

    /** 是否命中高风险元数据规则（如 EXIF Software 标注了 AI 生成工具）。 */
    private boolean suspicious;

    /** 元数据风险分 0~1，非 AI 生成概率。 */
    private double riskScore;

    /** 风险等级：HIGH / MEDIUM / LOW / NONE。 */
    private String level;

    /** 是否命中阻断规则（此时材料不予保存）。 */
    private boolean blocked;

    /** EXIF 中是否存在真实拍摄设备信息（Make / Model）。 */
    private boolean captureDevicePresent;

    private String reason;

    /** 判定算法标识，固定为 EXIF_METADATA_RULES。 */
    @Builder.Default
    private String algorithm = "EXIF_METADATA_RULES";

    /** 是否使用了视觉模型，恒为 false——显式声明，避免被误读为内容识别。 */
    @Builder.Default
    private boolean visualModelUsed = false;

    /* ---------------- 兼容旧客户端字段（语义已修正） ---------------- */

    /**
     * 旧字段：历史上被命名为 aiGenerated，实际含义是"命中高风险元数据规则"。
     * 保留输出以免已发布的 App 解析失败，新代码请使用 {@link #suspicious}。
     */
    @Deprecated
    @JsonProperty("aiGenerated")
    public boolean legacyAiGenerated() {
        return suspicious;
    }

    /**
     * 旧字段：历史上被命名为 confidence，实际含义是"元数据风险分"。
     * 保留输出以免已发布的 App 解析失败，新代码请使用 {@link #riskScore}。
     */
    @Deprecated
    @JsonProperty("confidence")
    public double legacyConfidence() {
        return riskScore;
    }
}
