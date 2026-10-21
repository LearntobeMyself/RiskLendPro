package org.example.risklendpro.cs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI 客服对话回复")
public class ChatReply {

    @Schema(description = "会话ID")
    private Long conversationId;

    @Schema(description = "回复内容")
    private String reply;

    @Schema(description = "意图路由：PRODUCT/REALTIME/SCRIPT/TRANSFER")
    private String intent;

    @Schema(description = "生成回复的模型；fixed-script 表示规则话术，非大模型")
    private String model;
}
