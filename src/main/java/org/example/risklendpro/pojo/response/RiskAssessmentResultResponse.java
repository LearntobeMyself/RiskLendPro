package org.example.risklendpro.pojo.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Schema(description = "风控评估结果响应")
public class RiskAssessmentResultResponse {
    @Schema(description = "申请ID")
    private String applyId;
    @Schema(description = "风控总分")
    private Integer totalScore;
    @Schema(description = "授信额度")
    private BigDecimal creditLimit;
    @Schema(description = "额度失效日期")
    private Date expireDate;
    @Schema(description = "状态")
    private String status;
    @Schema(description = "审批时间")
    private Date approvalTime;
}