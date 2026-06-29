package org.example.risklendpro.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "还款提醒请求")
public class RepaymentReminderRequest {
    private Long userId;
    private Long loanId;
    private Long planId;
    private String channel;
    private String message;
}
