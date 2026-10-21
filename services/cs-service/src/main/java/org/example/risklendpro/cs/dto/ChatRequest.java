package org.example.risklendpro.cs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "AI 客服对话请求")
public class ChatRequest {

    @Schema(description = "会话ID，首次对话可不传")
    private Long conversationId;

    @Schema(description = "用户消息")
    @NotBlank(message = "消息内容不能为空")
    private String message;
}
