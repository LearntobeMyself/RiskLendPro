package org.example.risklendpro.cs.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.common.security.SecurityUtils;
import org.example.risklendpro.cs.dto.TicketCreateRequest;
import org.example.risklendpro.cs.dto.TicketVO;
import org.example.risklendpro.cs.service.CsTicketService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "转人工工单（用户端）")
@RestController
@RequestMapping("/api/v1/cs")
public class CsTicketController {

    private final CsTicketService csTicketService;

    public CsTicketController(CsTicketService csTicketService) {
        this.csTicketService = csTicketService;
    }

    @Operation(summary = "提交转人工工单")
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/tickets")
    public CommonResponse<Map<String, Object>> create(@Valid @RequestBody TicketCreateRequest request) {
        Long ticketId = csTicketService.create(SecurityUtils.getUserId(), request);
        return CommonResponse.success("工单已提交", Map.of("ticketId", ticketId));
    }

    @Operation(summary = "我的工单列表")
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/tickets")
    public CommonResponse<List<TicketVO>> myTickets() {
        return CommonResponse.success("查询成功", csTicketService.myTickets(SecurityUtils.getUserId()));
    }
}
