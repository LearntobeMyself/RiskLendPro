package org.example.risklendpro.pojo.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Schema(description = "管理员审批贷款申请响应")
public class LoanApproveResponse {
    @Schema(description = "贷款ID")
    private Long loanId;
    @Schema(description = "用户ID")
    private Long userId;
    @Schema(description = "原额度")
    private BigDecimal originalLimit;
    @Schema(description = "额外批准的额度")
    private BigDecimal additionalLimit;
    @Schema(description = "总额度")
    private BigDecimal totalLimit;
    @Schema(description = "实际发放金额")
    private BigDecimal actualDisbursedAmount;
    @Schema(description = "状态")
    private String status;
    @Schema(description = "审批时间")
    private Date approveTime;
    @Schema(description = "拒绝原因")
    private String rejectReason;
    @Schema(description = "邮件是否发送")
    private boolean emailSent;
}