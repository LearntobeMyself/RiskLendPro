package org.example.risklendpro.cs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.risklendpro.cs.dto.ChatReply;
import org.example.risklendpro.cs.dto.ChatRequest;
import org.example.risklendpro.cs.dto.CsMessageVO;
import org.example.risklendpro.cs.dto.TicketCreateRequest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 客服核心：意图路由（产品/实时/敏感/转人工）+ 大模型生成 + 多轮记忆 + 消息落库。
 * 安全边界（提示词注入、越权、脱敏）在大模型之外强制校验；
 * 大模型判定知识库无依据（NO_ANSWER）时，在模型之外代码级强制转人工建单，防止编造业务信息。
 */
@Service
public class CsChatService {

    private static final Logger log = LoggerFactory.getLogger(CsChatService.class);

    /** 多轮上下文记忆保留的最近消息条数。 */
    private static final int HISTORY_LIMIT = 6;

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
    private static final String SCRIPT_INJECTION =
            "抱歉，我无法执行该请求。作为平台客服，我只能提供产品咨询和本人账户信息查询服务，"
            + "如有其他问题请转人工客服。";

    /** 大模型判定「知识库无依据」时必须输出的拒答标记（由系统提示词约定，代码检测强制转人工）。 */
    private static final String NO_ANSWER_MARK = "NO_ANSWER";
    /** 拒答转人工时的回复引导语。 */
    private static final String NO_ANSWER_LEAD =
            "您咨询的问题超出了我的解答范围，为保证信息准确，已为您转接人工客服。";
    /** 大模型拒答后转人工时，回复使用的模型标注（区别于 fixed-script 与普通 deepseek 回答）。 */
    private static final String MODEL_HANDOFF = "deepseek-handoff";

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
    private final CsTicketService ticketService;

    private volatile String systemPrompt;
    private volatile String productKnowledge;

    public CsChatService(CsConversationMapper conversationMapper,
                         CsMessageMapper messageMapper,
                         DeepSeekClient deepSeekClient,
                         RealtimeDataService realtimeDataService,
                         SensitiveGuard sensitiveGuard,
                         CsAuditService auditService,
                         CsTicketService ticketService) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.deepSeekClient = deepSeekClient;
        this.realtimeDataService = realtimeDataService;
        this.sensitiveGuard = sensitiveGuard;
        this.auditService = auditService;
        this.ticketService = ticketService;
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

        // 多轮记忆：在保存当前消息之前加载历史上下文（不含当前消息）
        List<Map<String, String>> history = loadHistory(conversation.getId(), HISTORY_LIMIT);

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
            generated = generate(userId, intent, safeMessage, conversation.getId(), history);
            // 大模型判定知识库无依据而转人工时，intent 归为 TRANSFER，便于前端与审计区分
            if (MODEL_HANDOFF.equals(generated.model())) {
                intent = "TRANSFER";
            }
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

    private GeneratedReply generate(Long userId, String intent, String message,
                                    Long conversationId, List<Map<String, String>> history) {
        switch (intent) {
            case "TRANSFER":
                return generateTransfer(userId, conversationId, message, null);
            case "SCRIPT":
                return GeneratedReply.script(SCRIPT_REJECT);
            case "REALTIME":
                return generateRealtime(userId, message);
            default:
                try {
                    LlmReply llm = deepSeekClient.chatWithHistory(buildSystemPrompt(), history, message);
                    String content = llm.getContent() == null ? "" : llm.getContent().trim();
                    // 防编造收口：大模型按系统提示词约定输出 NO_ANSWER 表示知识库无依据，
                    // 此处在模型之外代码级强制转人工建单，杜绝模型自行编造业务信息。
                    if (content.startsWith(NO_ANSWER_MARK)) {
                        log.info("大模型判定知识库无依据，自动转人工。userId={}, conversationId={}",
                                userId, conversationId);
                        auditService.record(userId, CsAuditService.ACTION_LLM_HANDOFF, "message",
                                String.valueOf(conversationId), truncate(message, 200));
                        return generateTransfer(userId, conversationId, message, NO_ANSWER_LEAD);
                    }
                    return new GeneratedReply(content, llm.getModel(), llm.getRequestId(),
                            llm.getPromptTokens(), llm.getCompletionTokens());
                } catch (Exception e) {
                    log.warn("大模型调用失败，降级为固定话术: {}", e.getMessage());
                    return GeneratedReply.script(SCRIPT_FALLBACK);
                }
        }
    }

    /**
     * 转人工：自动创建工单，并把工单号返回给用户。
     *
     * @param lead 回复引导语；null 表示用户主动转人工，使用默认引导语
     */
    private GeneratedReply generateTransfer(Long userId, Long conversationId, String message, String lead) {
        try {
            TicketCreateRequest req = new TicketCreateRequest();
            req.setConversationId(conversationId);
            req.setCategory(inferCategory(message));
            req.setDescription(message);
            req.setInvolvesMoney(involvesMoney(message) ? 1 : 0);
            Long ticketId = ticketService.create(userId, req);
            String head = (lead == null || lead.isBlank())
                    ? "正在为您转接人工客服，已为您生成工单（编号 #" + ticketId + "）。"
                    : lead + "已为您生成工单（编号 #" + ticketId + "）。";
            String content = head
                    + "请提供手机号和问题描述，人工客服会在服务时间内尽快处理。";
            // 拒答转人工（lead 非空）标注 deepseek-handoff，供上层把 intent 归为 TRANSFER
            boolean llmHandoff = lead != null && !lead.isBlank();
            return new GeneratedReply(content, llmHandoff ? MODEL_HANDOFF : "fixed-script",
                    null, null, null);
        } catch (Exception e) {
            log.warn("转人工自动建单失败，降级为引导话术: {}", e.getMessage());
            return GeneratedReply.script(SCRIPT_TRANSFER);
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
            case SensitiveGuard.PROMPT_INJECTION -> SCRIPT_INJECTION;
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

    /** 根据消息内容推断工单分类。 */
    private String inferCategory(String message) {
        if (containsAny(message, new String[]{"还款", "结清", "逾期", "账单", "扣款"})) return "REPAYMENT";
        if (containsAny(message, new String[]{"额度", "借款", "放款", "到账", "提现"})) return "LOAN";
        if (containsAny(message, new String[]{"评估", "审批", "拒绝", "风控"})) return "RISK";
        if (containsAny(message, new String[]{"账号", "登录", "密码", "身份", "冻结"})) return "ACCOUNT";
        return "OTHER";
    }

    private boolean involvesMoney(String message) {
        return containsAny(message, new String[]{
                "还款", "退款", "扣款", "金额", "到账", "额度", "利息", "费用", "结清", "提现"});
    }

    /** 加载会话最近 limit 条消息作为多轮上下文（不含当前消息），按时间正序返回。 */
    private List<Map<String, String>> loadHistory(Long conversationId, int limit) {
        LambdaQueryWrapper<CsMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CsMessage::getConversationId, conversationId)
                .orderByDesc(CsMessage::getCreateTime)
                .last("LIMIT " + limit);
        List<CsMessage> recent = messageMapper.selectList(wrapper);
        List<Map<String, String>> history = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            CsMessage m = recent.get(i);
            String role = "USER".equalsIgnoreCase(m.getRole()) ? "user" : "assistant";
            history.add(Map.of("role", role, "content", m.getContent() == null ? "" : m.getContent()));
        }
        return history;
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
