package org.example.risklendpro.cs.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.cs.config.CsLlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek（OpenAI 兼容）客户端。调用 POST {baseUrl}/chat/completions。
 */
@Component
public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private final CsLlmProperties props;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public DeepSeekClient(CsLlmProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restTemplate = buildRestTemplate();
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = props.getTimeoutSeconds() * 1000;
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    public LlmReply chat(String systemPrompt, String userMessage) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new IllegalStateException("cs.llm.api-key 未配置，无法调用大模型");
        }

        Map<String, Object> body = Map.of(
                "model", props.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                ),
                "max_tokens", props.getMaxTokens(),
                "temperature", props.getTemperature()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getApiKey());
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String url = props.getBaseUrl() + "/chat/completions";
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return parse(response.getBody());
        } catch (RestClientException e) {
            log.error("DeepSeek 调用失败: {}", e.getMessage());
            throw new IllegalStateException("大模型服务暂不可用，请稍后重试或转人工客服", e);
        }
    }

    private LlmReply parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            LlmReply reply = new LlmReply();
            reply.setContent(root.path("choices").path(0).path("message").path("content").asText(""));
            reply.setModel(root.path("model").asText(props.getModel()));
            reply.setRequestId(root.path("id").isMissingNode() ? null : root.path("id").asText(null));
            JsonNode usage = root.path("usage");
            reply.setPromptTokens(usage.path("prompt_tokens").isMissingNode() ? null : usage.path("prompt_tokens").asInt());
            reply.setCompletionTokens(usage.path("completion_tokens").isMissingNode() ? null : usage.path("completion_tokens").asInt());
            return reply;
        } catch (Exception e) {
            log.error("DeepSeek 响应解析失败: {}", json, e);
            throw new IllegalStateException("大模型返回异常，请稍后重试", e);
        }
    }
}
