package org.example.risklendpro.cs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.risklendpro.cs.dto.AdminConversationVO;
import org.example.risklendpro.cs.dto.CsMessageVO;
import org.example.risklendpro.cs.dto.TicketCloseRequest;
import org.example.risklendpro.cs.entity.CsConversation;
import org.example.risklendpro.cs.entity.CsMessage;
import org.example.risklendpro.cs.entity.CsTicket;
import org.example.risklendpro.cs.mapper.CsConversationMapper;
import org.example.risklendpro.cs.mapper.CsMessageMapper;
import org.example.risklendpro.cs.security.SensitiveDataMasker;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.example.risklendpro.cs.service.CsChatService.STATUS_AGENT;
import static org.example.risklendpro.cs.service.CsChatService.STATUS_CLOSED;
import static org.example.risklendpro.cs.service.CsChatService.STATUS_OPEN;
import static org.example.risklendpro.cs.service.CsChatService.STATUS_TRANSFERRED;

/**
 * 管理端人工客服接管：待接入会话列表、查看聊天记录、接管、回复、结束。
 * 转人工后的「人工在同一会话界面回复」由本服务与用户端 history 接口共同完成：
 * 人工回复以 role=AGENT 写入同一张 cs_message 表，用户端轮询即可看到。
 */
@Service
public class AdminCsChatService {

    private static final String TAKEOVER_NOTICE = "人工客服已接入，请问有什么可以帮您？";
    private static final String CLOSE_RESULT = "人工客服已处理完毕";

    private final CsConversationMapper conversationMapper;
    private final CsMessageMapper messageMapper;
    private final CsTicketService ticketService;
    private final CsAuditService auditService;

    public AdminCsChatService(CsConversationMapper conversationMapper,
                              CsMessageMapper messageMapper,
                              CsTicketService ticketService,
                              CsAuditService auditService) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.ticketService = ticketService;
        this.auditService = auditService;
    }

    /** 待接入/接待中的会话列表（默认排除 OPEN 与 CLOSED）。 */
    public List<AdminConversationVO> listConversations(String status) {
        LambdaQueryWrapper<CsConversation> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(CsConversation::getStatus, status);
        } else {
            wrapper.in(CsConversation::getStatus, STATUS_TRANSFERRED, STATUS_AGENT);
        }
        wrapper.orderByDesc(CsConversation::getUpdateTime);
        return conversationMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    /** 查看会话完整聊天记录（管理端，不校验会话归属）。 */
    public List<CsMessageVO> messages(Long conversationId) {
        requireConversation(conversationId);
        LambdaQueryWrapper<CsMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CsMessage::getConversationId, conversationId)
                .orderByAsc(CsMessage::getCreateTime);
        return messageMapper.selectList(wrapper).stream().map(this::toMessageVO).toList();
    }

    /** 接管会话：TRANSFERRED -> AGENT，写入接入提示，并联动工单置为处理中。 */
    public AdminConversationVO takeover(Long conversationId, Long adminId) {
        CsConversation conversation = requireConversation(conversationId);
        if (!STATUS_TRANSFERRED.equals(conversation.getStatus())) {
            throw new IllegalArgumentException("该会话当前不在待接入状态，无法接管");
        }
        conversation.setStatus(STATUS_AGENT);
        conversation.setLastMessage(TAKEOVER_NOTICE);
        conversationMapper.updateById(conversation);

        saveAgentMessage(conversationId, adminId, TAKEOVER_NOTICE, "HANDOFF");

        CsTicket ticket = ticketService.findOpenTicketByConversation(conversationId);
        if (ticket != null) {
            ticketService.process(ticket.getId(), adminId);
        }
        auditService.record(adminId, CsAuditService.ACTION_AGENT_TAKEOVER, "conversation",
                String.valueOf(conversationId), "handler=" + adminId);
        return toVO(conversation);
    }

    /** 人工回复：以 role=AGENT 写入会话，用户端轮询可见。 */
    public CsMessageVO reply(Long conversationId, Long adminId, String content) {
        CsConversation conversation = requireConversation(conversationId);
        if (!STATUS_AGENT.equals(conversation.getStatus())) {
            throw new IllegalArgumentException("请先接管会话，再回复用户");
        }
        String safeContent = SensitiveDataMasker.mask(content);
        conversation.setLastMessage(truncate(safeContent, 500));
        conversationMapper.updateById(conversation);

        saveAgentMessage(conversationId, adminId, safeContent, "HANDOFF");
        auditService.record(adminId, CsAuditService.ACTION_AGENT_REPLY, "conversation",
                String.valueOf(conversationId), truncate(safeContent, 200));
        return toMessageVO(lastAgentMessage(conversationId));
    }

    /** 结束会话：会话置 CLOSED，联动工单关闭。 */
    public AdminConversationVO closeConversation(Long conversationId, Long adminId) {
        CsConversation conversation = requireConversation(conversationId);
        if (STATUS_CLOSED.equals(conversation.getStatus())) {
            throw new IllegalArgumentException("会话已结束");
        }
        if (STATUS_OPEN.equals(conversation.getStatus())) {
            throw new IllegalArgumentException("AI 接待中的会话无需人工结束");
        }
        conversation.setStatus(STATUS_CLOSED);
        conversationMapper.updateById(conversation);

        CsTicket ticket = ticketService.findOpenTicketByConversation(conversationId);
        if (ticket != null) {
            TicketCloseRequest req = new TicketCloseRequest();
            req.setResult(CLOSE_RESULT);
            ticketService.close(ticket.getId(), adminId, req);
        }
        auditService.record(adminId, CsAuditService.ACTION_AGENT_CLOSE, "conversation",
                String.valueOf(conversationId), "handler=" + adminId);
        return toVO(conversation);
    }

    private void saveAgentMessage(Long conversationId, Long adminId, String content, String intent) {
        CsMessage msg = new CsMessage();
        msg.setConversationId(conversationId);
        msg.setUserId(adminId);
        msg.setRole("AGENT");
        msg.setContent(content);
        msg.setIntent(intent);
        msg.setModel("agent");
        messageMapper.insert(msg);
    }

    private CsMessage lastAgentMessage(Long conversationId) {
        LambdaQueryWrapper<CsMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CsMessage::getConversationId, conversationId)
                .eq(CsMessage::getRole, "AGENT")
                .orderByDesc(CsMessage::getCreateTime)
                .last("LIMIT 1");
        return messageMapper.selectOne(wrapper);
    }

    private CsConversation requireConversation(Long conversationId) {
        CsConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        return conversation;
    }

    private AdminConversationVO toVO(CsConversation c) {
        AdminConversationVO vo = new AdminConversationVO();
        vo.setId(c.getId());
        vo.setUserId(c.getUserId());
        vo.setStatus(c.getStatus());
        vo.setLastMessage(c.getLastMessage());
        vo.setCreateTime(c.getCreateTime());
        vo.setUpdateTime(c.getUpdateTime());
        return vo;
    }

    private CsMessageVO toMessageVO(CsMessage m) {
        CsMessageVO vo = new CsMessageVO();
        vo.setRole(m.getRole());
        vo.setContent(m.getContent());
        vo.setIntent(m.getIntent());
        vo.setCreateTime(m.getCreateTime());
        return vo;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
