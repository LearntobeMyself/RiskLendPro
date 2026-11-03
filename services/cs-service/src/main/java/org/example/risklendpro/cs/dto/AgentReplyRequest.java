package org.example.risklendpro.cs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "人工客服回复请求")
@Data
public class AgentReplyRequest {

    @Schema(description = "回复内容")
    @NotBlank(message = "回复内容不能为空")
    private String content;
}
