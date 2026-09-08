package org.example.risklendpro.risk.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "管理员审批风控评估请求")
public class RiskApproveRequest {
    @Schema(description = "申请ID", example = "L20231027001")
    private String applyId;
    @Schema(description = "PASS(通过) 或 REJECT(拒绝)", example = "PASS")
    private String auditResult;
    @Schema(description = "最终额度（通过时必填）", example = "20000.00")
    private BigDecimal creditLimit;
    @Schema(description = "审批评语", example = "信用良好，审批通过")
    private String auditRemark;
}