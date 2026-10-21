package org.example.risklendpro.loan.borrow;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "借款请求")
public class LoanRequest {
    @Schema(description = "借款金额", example = "5000.00")
    @NotNull(message = "借款金额不能为空")
    @DecimalMin(value = "100", message = "借款金额最低 100 元")
    @Max(value = 5_000_000, message = "单笔借款金额不能超过 500 万元")
    private BigDecimal amount;
    @Schema(description = "还款期限（月）", example = "12")
    @NotNull(message = "借款期限不能为空")
    @Min(value = 1, message = "借款期限至少 1 个月")
    @Max(value = 60, message = "借款期限不能超过 60 个月")
    private Integer termMonths;
    @Schema(description = "还款方式（等额本息/等额本金/先息后本），也可传产品枚举码 EQUAL_INSTALLMENT 等", example = "等额本息")
    private String repaymentMethod;
    @Schema(description = "目录产品ID；为空则为直接借款")
    private Long productId;
}
