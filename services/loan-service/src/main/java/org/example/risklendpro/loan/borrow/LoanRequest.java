package org.example.risklendpro.loan.borrow;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "借款请求")
public class LoanRequest {
    @Schema(description = "借款金额", example = "5000.00")
    private BigDecimal amount;
    @Schema(description = "还款期限（月）", example = "12")
    private Integer termMonths;
    @Schema(description = "还款方式（等额本息/等额本金/先息后本），也可传产品枚举码 EQUAL_INSTALLMENT 等", example = "等额本息")
    private String repaymentMethod;
    @Schema(description = "目录产品ID；为空则为直接借款")
    private Long productId;
}