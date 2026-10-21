package org.example.risklendpro.cs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.risklendpro.cs.dto.ChatReply;
import org.example.risklendpro.cs.dto.ChatRequest;
import org.example.risklendpro.cs.dto.CsMessageVO;
import org.example.risklendpro.cs.entity.CsConversation;
import org.example.risklendpro.cs.entity.CsMessage;
import org.example.risklendpro.cs.llm.DeepSeekClient;
import org.example.risklendpro.cs.llm.LlmReply;
import org.example.risklendpro.cs.mapper.CsConversationMapper;
import org.example.risklendpro.cs.mapper.CsMessageMapper;
import org.example.risklendpro.cs.security.SensitiveDataMasker;
import org.example.risklendpro.cs.security.SensitiveGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * AI 客服核心：意图路由（产品/实时/敏感/转人工）+ 大模型生成 + 消息落库。
 */
@Service
public class CsChatService {

    private static final Logger log = LoggerFactory.getLogger(CsChatService.class);

    private static final String SCRIPT_REJECT =
            "贷款审批会综合评估多项因素，平台不能对外披露具体风控规则、评分明细和内部判断依据。"
            + "本次结果不必然代表以后也不能申请，您可以关注页面显示的再次申请条件。如对结果有疑问，可以申请人工客服进一步核实。";
    private static final String SCRIPT_TRANSFER =
            "正在为您申请转人工客服，请提供手机号、业务编号和问题描述。人工客服会在服务时间内处理。";
    private static final String SCRIPT_REALTIME =
            "抱歉，实时查询您的账户信息时出现问题，建议您在 App 内查看，或转人工客服核实。";
    private static final String SCRIPT_FALLBACK =
            "抱歉，我暂时无法处理这个问题，请稍后重试或转人工客服。";
    private static final String SCRIPT_UNAUTHORIZED =
            "出于隐私保护，客服无法查询他人的账户信息。请您使用本人账号登录后查询，或由本人联系客服处理。";
    private static final String SCRIPT_PRIVACY =
            "检测到您发送了身份证号、银行卡号等敏感信息。为保护您的隐私，请不要在对话中发送完整敏感信息，"
            + "相关内容已自动脱敏处理；如需上传身份材料，请使用 App 内的上传入口。";

    private static final String[] TRANSFER_KEYS = {"转人工", "人工客服", "投诉", "客服电话"};
    private static final String[] SCRIPT_KEYS = {"被拒", "拒绝", "为什么没通过", "不通过", "凭什么", "批不了"};
    private static final String[] PRODUCT_NAME_KEYS = {
            "薪金闪贷", "随心周转金", "公积金", "公职", "经营周转", "商户快贷",
            "安居房抵", "途安车抵", "家装", "教育分期", "数码分期", "暑期"
    };
    private static final String[] REALTIME_KEYS = {
            "我的额度", "我还能借", "我的评估", "我的借款", "我的还款", "我下一期",
            "我有没有逾期", "额度还剩", "我的申请", "我的状态", "到哪一步", "补充材料", "下一期"
    };

    private final CsConversationMapper conversationMapper;
    private final CsMessageMapper messageMapper;
    private final DeepSeekClient deepSeekClient;
    private final RealtimeDataService realtimeDataService;
    private final SensitiveGuard sensitiveGuard;
    private final CsAuditService auditService;

    private volatile String systemPrompt;
    private volatile String productKnowledge;

    public CsChatService(CsConversationMapper conversationMapper,
                         CsMessageMapper messageMapper,
                         DeepSeekClient deepSeekClient,
                         RealtimeDataService realtimeDataService,
                         SensitiveGuard sensitiveGuard,
                         CsAuditService auditService) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.deepSeekClient = deepSeekClient;
        this.realtimeDataService = realtimeDataService;
        this.sensitiveGuard = sensitiveGuard;
        this.auditService = auditService;
    }

    public ChatReply chat(Long userId, ChatRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        // 敏感检测用原始消息，先于脱敏判断是否命中隐私数据
        String sensitiveType = sensitiveGuard.detect(message);
        // 脱敏：落库与大模型输入均使用脱敏后的内容，避免敏感信息明文入库或外发
        String safeMessage = SensitiveDataMasker.mask(message);

        CsConversation conversation = getOrCreateConversation(userId, request.getConversationId());
        saveMessage(conversation.getId(), userId, "USER", safeMessage, null, null, null, null, null);

        String intent;
        GeneratedReply generated;
        if (sensitiveType != null) {
            intent = "SCRIPT";
            generated = sensitiveReply(sensitiveType);
            auditService.record(userId, CsAuditService.ACTION_SENSITIVE_BLOCK, "message",
                    String.valueOf(conversation.getId()), sensitiveType + ":" + truncate(safeMessage, 200));
        } else {
            intent = routeIntent(safeMessage);
            generated = generate(userId, intent, safeMessage);
            if ("REALTIME".equals(intent)) {
                auditService.record(userId, CsAuditService.ACTION_REALTIME_QUERY, "message",
                        String.valueOf(conversation.getId()), truncate(safeMessage, 200));
            }
        }

        // 返回前对 AI 回复再做一次脱敏兜底
        String safeReply = SensitiveDataMasker.mask(generated.content);
        saveMessage(conversation.getId(), userId, "ASSISTANT", safeReply, intent,
                generated.model, generated.requestId, generated.promptTokens, generated.completionTokens);

        conversation.setLastMessage(truncate(safeReply, 500));
        conversationMapper.updateById(conversation);

        ChatReply reply = new ChatReply();
        reply.setConversationId(conversation.getId());
        reply.setReply(safeReply);
        reply.setIntent(intent);
        reply.setModel(generated.model);
        return reply;
    }

    public List<CsMessageVO> history(Long userId, Long conversationId) {
        CsConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }
        LambdaQueryWrapper<CsMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CsMessage::getConversationId, conversationId)
                .orderByAsc(CsMessage::getCreateTime);
        return messageMapper.selectList(wrapper).stream()
                .map(m -> {
                    CsMessageVO vo = new CsMessageVO();
                    vo.setRole(m.getRole());
                    vo.setContent(m.getContent());
                    vo.setIntent(m.getIntent());
                    vo.setCreateTime(m.getCreateTime());
                    return vo;
                })
                .toList();
    }

    private GeneratedReply generate(Long userId, String intent, String message) {
        switch (intent) {
            case "TRANSFER":
                return GeneratedReply.script(SCRIPT_TRANSFER);
            case "SCRIPT":
                return GeneratedReply.script(SCRIPT_REJECT);
            case "REALTIME":
                return generateRealtime(userId, message);
            default:
                try {
                    LlmReply llm = deepSeekClient.chat(buildSystemPrompt(), message);
                    return new GeneratedReply(llm.getContent(), llm.getModel(), llm.getRequestId(),
                            llm.getPromptTokens(), llm.getCompletionTokens());
                } catch (Exception e) {
                    log.warn("大模型调用失败，降级为固定话术: {}", e.getMessage());
                    return GeneratedReply.script(SCRIPT_FALLBACK);
                }
        }
    }

    private GeneratedReply generateRealtime(Long userId, String message) {
        try {
            String content = realtimeDataService.describe(userId, message);
            return new GeneratedReply(content, "feign-realtime", null, null, null);
        } catch (Exception e) {
            log.warn("实时数据查询失败，降级为固定话术: {}", e.getMessage());
            return GeneratedReply.script(SCRIPT_REALTIME);
        }
    }

    private GeneratedReply sensitiveReply(String sensitiveType) {
        String content = switch (sensitiveType) {
            case SensitiveGuard.UNAUTHORIZED -> SCRIPT_UNAUTHORIZED;
            case SensitiveGuard.PRIVACY_DATA -> SCRIPT_PRIVACY;
            default -> SCRIPT_REJECT;
        };
        return GeneratedReply.script(content);
    }

    private String routeIntent(String message) {
        if (containsAny(message, TRANSFER_KEYS)) return "TRANSFER";
        if (containsAny(message, SCRIPT_KEYS)) return "SCRIPT";
        if (containsAny(message, PRODUCT_NAME_KEYS)) return "PRODUCT";
        if (containsAny(message, REALTIME_KEYS)) return "REALTIME";
        return "PRODUCT";
    }

    private CsConversation getOrCreateConversation(Long userId, Long conversationId) {
        if (conversationId != null) {
            CsConversation existing = conversationMapper.selectById(conversationId);
            if (existing != null && existing.getUserId().equals(userId)) {
                return existing;
            }
        }
        CsConversation conversation = new CsConversation();
        conversation.setUserId(userId);
        conversation.setStatus("OPEN");
        conversationMapper.insert(conversation);
        return conversation;
    }

    private void saveMessage(Long conversationId, Long userId, String role, String content, String intent,
                             String model, String requestId, Integer promptTokens, Integer completionTokens) {
        CsMessage msg = new CsMessage();
        msg.setConversationId(conversationId);
        msg.setUserId(userId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setIntent(intent);
        msg.setModel(model);
        msg.setRequestId(requestId);
        msg.setPromptTokens(promptTokens);
        msg.setCompletionTokens(completionTokens);
        messageMapper.insert(msg);
    }

    private String buildSystemPrompt() {
        return getSystemPrompt() + "\n\n【产品知识】\n" + getProductKnowledge();
    }

    private String getSystemPrompt() {
        if (systemPrompt == null) {
            systemPrompt = loadResource("cs/cs-system-prompt.txt");
        }
        return systemPrompt;
    }

    private String getProductKnowledge() {
        if (productKnowledge == null) {
            productKnowledge = loadResource("cs/cs-product-knowledge.txt");
        }
        return productKnowledge;
    }

    private String loadResource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载客服知识资源失败: " + path, e);
        }
    }

    private boolean containsAny(String message, String[] keys) {
        for (String key : keys) {
            if (message.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record GeneratedReply(String content, String model, String requestId,
                                  Integer promptTokens, Integer completionTokens) {
        static GeneratedReply script(String content) {
            return new GeneratedReply(content, "fixed-script", null, null, null);
        }
    }
}
