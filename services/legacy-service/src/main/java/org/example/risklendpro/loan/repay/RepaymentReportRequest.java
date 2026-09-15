package org.example.risklendpro.loan.repay;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "还款报表生成请求")
public class RepaymentReportRequest {
    private String startDate;
    private String endDate;
    private String type;
}
