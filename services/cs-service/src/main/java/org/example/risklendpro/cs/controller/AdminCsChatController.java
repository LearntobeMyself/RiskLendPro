package org.example.risklendpro.cs.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.common.security.SecurityUtils;
import org.example.risklendpro.cs.dto.AdminConversationVO;
import org.example.risklendpro.cs.dto.AgentReplyRequest;
import org.example.risklendpro.cs.dto.CsMessageVO;
import org.example.risklendpro.cs.service.AdminCsChatService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端人工客服接管（同一个聊天界面内人工回复）。
 * 权限沿用客服工单权限：查看 cs:ticket，写操作 cs:ticket:write。
 */
@Tag(name = "人工客服接管（管理端）")
@RestController
@RequestMapping("/api/v1/admin/cs")
@PreAuthorize("hasAuthority('cs:ticket')")
public class AdminCsChatController {

    private final AdminCsChatService adminCsChatService;

    public AdminCsChatController(AdminCsChatService adminCsChatService) {
        this.adminCsChatService = adminCsChatService;
    }

    @Operation(summary = "待接入/接待中的会话列表")
    @GetMapping("/conversations")
    public CommonResponse<List<AdminConversationVO>> conversations(
            @RequestParam(required = false) String status) {
        return CommonResponse.success("查询成功", adminCsChatService.listConversations(status));
    }

    @Operation(summary = "会话聊天记录")
    @GetMapping("/conversations/{conversationId}/messages")
    public CommonResponse<List<CsMessageVO>> messages(@PathVariable Long conversationId) {
        return CommonResponse.success("查询成功", adminCsChatService.messages(conversationId));
    }

    @Operation(summary = "接管会话（转人工后由人工接入）")
    @PreAuthorize("hasAuthority('cs:ticket:write')")
    @PostMapping("/conversations/{conversationId}/takeover")
    public CommonResponse<AdminConversationVO> takeover(@PathVariable Long conversationId) {
        return CommonResponse.success("已接管",
                adminCsChatService.takeover(conversationId, SecurityUtils.getAdminId()));
    }

    @Operation(summary = "人工回复用户")
    @PreAuthorize("hasAuthority('cs:ticket:write')")
    @PostMapping("/conversations/{conversationId}/reply")
    public CommonResponse<CsMessageVO> reply(@PathVariable Long conversationId,
                                             @Valid @RequestBody AgentReplyRequest request) {
        return CommonResponse.success("已回复",
                adminCsChatService.reply(conversationId, SecurityUtils.getAdminId(), request.getContent()));
    }

    @Operation(summary = "结束会话")
    @PreAuthorize("hasAuthority('cs:ticket:write')")
    @PostMapping("/conversations/{conversationId}/close")
    public CommonResponse<AdminConversationVO> close(@PathVariable Long conversationId) {
        return CommonResponse.success("已结束",
                adminCsChatService.closeConversation(conversationId, SecurityUtils.getAdminId()));
    }
}
