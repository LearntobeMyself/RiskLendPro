package org.example.risklendpro.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "反欺诈告警处理请求")
public class AntiFraudHandleRequest {
    @Schema(description = "处理动作：CONFIRM / DISMISS / ESCALATE")
    private String action;
    @Schema(description = "处理备注")
    private String remark;
}
