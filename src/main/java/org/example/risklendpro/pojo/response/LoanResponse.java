package org.example.risklendpro.pojo.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Schema(description = "借款响应")
public class LoanResponse {
    @Schema(description = "借款ID")
    private Long loanId;
    @Schema(description = "用户ID")
    private Long userId;
    @Schema(description = "借款金额")
    private BigDecimal amount;
    @Schema(description = "还款期限（月）")
    private Integer termMonths;
    @Schema(description = "利率")
    private BigDecimal interestRate;
    @Schema(description = "还款方式")
    private String repaymentMethod;
    @Schema(description = "状态")
    private String status;
    @Schema(description = "放款时间")
    private Date disbursementTime;
    @Schema(description = "申请时间")
    private Date applyTime;
    @Schema(description = "是否自动审批")
    private Boolean autoApproved;
    @Schema(description = "备注")
    private String remark;
}