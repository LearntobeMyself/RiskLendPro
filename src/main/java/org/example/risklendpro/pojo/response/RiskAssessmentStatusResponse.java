package org.example.risklendpro.pojo.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "风控评估状态响应")
public class RiskAssessmentStatusResponse {
    @Schema(description = "申请ID")
    private String applyId;
    @Schema(description = "状态")
    private String status;
    @Schema(description = "状态标题")
    private String statusTitle;
    @Schema(description = "是否终态")
    private boolean isFinal;
}