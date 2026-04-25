package org.example.risklendpro.pojo.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Date;

@Data
@Schema(description = "风控评估申请响应")
public class RiskAssessmentResponse {
    @Schema(description = "申请ID")
    private String applyId;
    @Schema(description = "提交时间")
    private Date submitTime;
    @Schema(description = "状态")
    private String status;
}