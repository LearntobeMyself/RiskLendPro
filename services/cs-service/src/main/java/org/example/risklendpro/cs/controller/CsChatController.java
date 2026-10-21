package org.example.risklendpro.cs.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.common.security.SecurityUtils;
import org.example.risklendpro.cs.dto.ChatReply;
import org.example.risklendpro.cs.dto.ChatRequest;
import org.example.risklendpro.cs.dto.CsMessageVO;
import org.example.risklendpro.cs.service.CsChatService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AI 客服（用户端）")
@RestController
@RequestMapping("/api/v1/cs")
public class CsChatController {

    private final CsChatService csChatService;

    public CsChatController(CsChatService csChatService) {
        this.csChatService = csChatService;
    }

    @Operation(summary = "AI 客服对话")
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/chat")
    public CommonResponse<ChatReply> chat(@Valid @RequestBody ChatRequest request) {
        return CommonResponse.success("回复成功", csChatService.chat(SecurityUtils.getUserId(), request));
    }

    @Operation(summary = "会话历史消息")
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/conversations/{conversationId}/messages")
    public CommonResponse<List<CsMessageVO>> messages(@PathVariable Long conversationId) {
        return CommonResponse.success("查询成功", csChatService.history(SecurityUtils.getUserId(), conversationId));
    }
}
