package org.example.risklendpro.cs.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.common.security.SecurityUtils;
import org.example.risklendpro.cs.dto.TicketCloseRequest;
import org.example.risklendpro.cs.dto.TicketVO;
import org.example.risklendpro.cs.service.CsTicketService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "转人工工单（管理端）")
@RestController
@RequestMapping("/api/v1/admin/cs")
@PreAuthorize("hasAuthority('cs:ticket')")
public class AdminCsTicketController {

    private final CsTicketService csTicketService;

    public AdminCsTicketController(CsTicketService csTicketService) {
        this.csTicketService = csTicketService;
    }

    @Operation(summary = "工单列表")
    @GetMapping("/tickets")
    public CommonResponse<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return CommonResponse.success("查询成功", csTicketService.list(status, pageNum, pageSize));
    }

    @Operation(summary = "工单详情")
    @GetMapping("/tickets/{ticketId}")
    public CommonResponse<TicketVO> detail(@PathVariable Long ticketId) {
        return CommonResponse.success("查询成功", csTicketService.detail(ticketId));
    }

    @Operation(summary = "领取工单（开始处理）")
    @PreAuthorize("hasAuthority('cs:ticket:write')")
    @PostMapping("/tickets/{ticketId}/process")
    public CommonResponse<TicketVO> process(@PathVariable Long ticketId) {
        return CommonResponse.success("已领取", csTicketService.process(ticketId, SecurityUtils.getAdminId()));
    }

    @Operation(summary = "关闭工单")
    @PreAuthorize("hasAuthority('cs:ticket:write')")
    @PostMapping("/tickets/{ticketId}/close")
    public CommonResponse<TicketVO> close(@PathVariable Long ticketId,
                                          @Valid @RequestBody TicketCloseRequest request) {
        return CommonResponse.success("已关闭", csTicketService.close(ticketId, SecurityUtils.getAdminId(), request));
    }
}
