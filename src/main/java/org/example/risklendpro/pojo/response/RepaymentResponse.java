package org.example.risklendpro.pojo.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Schema(description = "还款响应")
public class RepaymentResponse {
    @Schema(description = "还款记录ID")
    private Long recordId;
    @Schema(description = "还款计划ID")
    private Long planId;
    @Schema(description = "期数")
    private Integer period;
    @Schema(description = "实际还款金额")
    private BigDecimal actualAmount;
    @Schema(description = "还款日期")
    private Date repaymentDate;
    @Schema(description = "状态")
    private String status;
}