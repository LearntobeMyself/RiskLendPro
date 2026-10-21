package org.example.risklendpro.cs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.cs.dto.TicketCloseRequest;
import org.example.risklendpro.cs.dto.TicketCreateRequest;
import org.example.risklendpro.cs.dto.TicketVO;
import org.example.risklendpro.cs.entity.CsTicket;
import org.example.risklendpro.cs.mapper.CsTicketMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 转人工工单：用户提交、我的工单、管理端列表/详情/领取/关单。
 * 状态流转：PENDING -> PROCESSING -> CLOSED。
 */
@Service
public class CsTicketService {

    private final CsTicketMapper ticketMapper;
    private final CsAuditService auditService;

    public CsTicketService(CsTicketMapper ticketMapper, CsAuditService auditService) {
        this.ticketMapper = ticketMapper;
        this.auditService = auditService;
    }

    public Long create(Long userId, TicketCreateRequest req) {
        CsTicket ticket = new CsTicket();
        ticket.setUserId(userId);
        ticket.setConversationId(req.getConversationId());
        ticket.setCategory(req.getCategory());
        ticket.setDescription(req.getDescription());
        ticket.setInvolvesMoney(req.getInvolvesMoney() == null ? 0 : req.getInvolvesMoney());
        ticket.setBizId(req.getBizId());
        ticket.setStatus("PENDING");
        ticketMapper.insert(ticket);
        auditService.record(userId, CsAuditService.ACTION_TICKET_CREATE, "ticket",
                String.valueOf(ticket.getId()), "category=" + req.getCategory());
        return ticket.getId();
    }

    public List<TicketVO> myTickets(Long userId) {
        LambdaQueryWrapper<CsTicket> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CsTicket::getUserId, userId).orderByDesc(CsTicket::getCreateTime);
        return ticketMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    public Map<String, Object> list(String status, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<CsTicket> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(CsTicket::getStatus, status);
        }
        wrapper.orderByDesc(CsTicket::getCreateTime);
        Page<CsTicket> page = ticketMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("total", page.getTotal());
        result.put("records", page.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    public TicketVO detail(Long ticketId) {
        return toVO(require(ticketId));
    }

    public TicketVO process(Long ticketId, Long adminId) {
        CsTicket ticket = require(ticketId);
        if (!"PENDING".equals(ticket.getStatus())) {
            throw new IllegalArgumentException("工单当前状态不可领取");
        }
        ticket.setStatus("PROCESSING");
        ticket.setHandlerId(adminId);
        ticketMapper.updateById(ticket);
        auditService.record(adminId, CsAuditService.ACTION_TICKET_PROCESS, "ticket",
                String.valueOf(ticketId), "handler=" + adminId);
        return toVO(ticket);
    }

    public TicketVO close(Long ticketId, Long adminId, TicketCloseRequest req) {
        CsTicket ticket = require(ticketId);
        if ("CLOSED".equals(ticket.getStatus())) {
            throw new IllegalArgumentException("工单已关闭");
        }
        ticket.setStatus("CLOSED");
        ticket.setHandlerId(adminId);
        ticket.setResult(req.getResult());
        ticket.setCloseTime(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        auditService.record(adminId, CsAuditService.ACTION_TICKET_CLOSE, "ticket",
                String.valueOf(ticketId), "handler=" + adminId);
        return toVO(ticket);
    }

    private CsTicket require(Long ticketId) {
        CsTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        return ticket;
    }

    private TicketVO toVO(CsTicket t) {
        TicketVO vo = new TicketVO();
        vo.setId(t.getId());
        vo.setUserId(t.getUserId());
        vo.setConversationId(t.getConversationId());
        vo.setCategory(t.getCategory());
        vo.setDescription(t.getDescription());
        vo.setInvolvesMoney(t.getInvolvesMoney());
        vo.setBizId(t.getBizId());
        vo.setStatus(t.getStatus());
        vo.setHandlerId(t.getHandlerId());
        vo.setResult(t.getResult());
        vo.setCreateTime(t.getCreateTime());
        vo.setCloseTime(t.getCloseTime());
        return vo;
    }
}
