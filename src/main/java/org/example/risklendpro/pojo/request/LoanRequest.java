package org.example.risklendpro.pojo.request;

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
    @Schema(description = "还款方式（等额本息/等额本金/先息后本）", example = "等额本息")
    private String repaymentMethod;
}