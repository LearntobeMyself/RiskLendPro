package org.example.risklendpro.pojo.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "可否提交风控评估")
public class RiskAssessmentSubmitEligibilityResponse {

    @Schema(description = "是否允许提交新申请")
    private boolean canSubmit;

    @Schema(description = "不可提交时的说明")
    private String reason;

    @Schema(description = "已有申请的 applyId")
    private String existingApplyId;

    @Schema(description = "已有申请的状态")
    private String existingStatus;

    @Schema(description = "冷却剩余天数（仅 SYSTEM_REJECT 30 天内）")
    private Long retryAfterDays;
}
