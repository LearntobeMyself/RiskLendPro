package org.example.risklendpro.cs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "转人工工单创建请求")
public class TicketCreateRequest {

    @Schema(description = "来源会话ID（可选）")
    private Long conversationId;

    @Schema(description = "问题分类：ACCOUNT/RISK/LOAN/REPAYMENT/OTHER")
    @NotBlank(message = "问题分类不能为空")
    private String category;

    @Schema(description = "问题描述")
    @NotBlank(message = "问题描述不能为空")
    private String description;

    @Schema(description = "是否涉及资金 0/1")
    private Integer involvesMoney;

    @Schema(description = "优先级：LOW/MEDIUM/HIGH（可留空，由系统按内容推断）")
    private String priority;

    @Schema(description = "关联业务ID（申请编号/借款编号等）")
    private String bizId;
}
