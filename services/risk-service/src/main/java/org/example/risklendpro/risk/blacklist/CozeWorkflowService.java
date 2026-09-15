package org.example.risklendpro.risk.blacklist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.risklendpro.risk.blacklist.CozeWorkflowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CozeWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(CozeWorkflowService.class);

    private final CozeWorkflowProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CozeWorkflowService(CozeWorkflowProperties properties, RestTemplate cozeRestTemplate) {
        this.properties = properties;
        this.restTemplate = cozeRestTemplate;
    }

    /**
     * 触发扣子工作流（黑名单爬取 + 回调 /sync/blacklist 由工作流内部完成）。
     */
    public CozeWorkflowResult run() {
        validateConfig();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getToken());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workflow_id", properties.getWorkflowId());
        if (properties.getWorkflowVersion() != null && !properties.getWorkflowVersion().isBlank()) {
            body.put("workflow_version", properties.getWorkflowVersion());
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        log.info("Triggering Coze workflow, workflowId={}, version={}",
                properties.getWorkflowId(), properties.getWorkflowVersion());

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    properties.getApiUrl(), request, String.class);
            String responseBody = response.getBody();
            log.info("Coze workflow response status={}, bodyLength={}",
                    response.getStatusCode().value(),
                    responseBody != null ? responseBody.length() : 0);

            return parseResponse(response.getStatusCode().value(), responseBody);
        } catch (RestClientResponseException e) {
            log.error("Coze workflow HTTP error, status={}, body={}",
                    e.getStatusCode().value(), truncate(e.getResponseBodyAsString()), e);
            return CozeWorkflowResult.failure(
                    "HTTP " + e.getStatusCode().value() + ": " + truncate(e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("Coze workflow call failed: {}", e.getMessage(), e);
            return CozeWorkflowResult.failure(e.getMessage());
        }
    }

    private void validateConfig() {
        if (properties.getToken() == null || properties.getToken().isBlank()) {
            throw new IllegalStateException("risk.coze.token 未配置，请设置 COZE_API_TOKEN 环境变量");
        }
        if (properties.getWorkflowId() == null || properties.getWorkflowId().isBlank()) {
            throw new IllegalStateException("risk.coze.workflow-id 未配置");
        }
    }

    private CozeWorkflowResult parseResponse(int httpStatus, String body) {
        if (body == null || body.isBlank()) {
            return httpStatus >= 200 && httpStatus < 300
                    ? CozeWorkflowResult.success(null, "empty body")
                    : CozeWorkflowResult.failure("empty response body");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            int code = root.path("code").asInt(-1);
            String msg = root.path("msg").asText("");
            if (code == 0 || (code == -1 && httpStatus >= 200 && httpStatus < 300)) {
                return CozeWorkflowResult.success(body, msg.isBlank() ? "ok" : msg);
            }
            return CozeWorkflowResult.failure("code=" + code + ", msg=" + msg);
        } catch (Exception e) {
            if (httpStatus >= 200 && httpStatus < 300) {
                return CozeWorkflowResult.success(body, "non-json response");
            }
            return CozeWorkflowResult.failure("parse error: " + e.getMessage());
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    public record CozeWorkflowResult(boolean success, String responseBody, String message) {
        static CozeWorkflowResult success(String body, String message) {
            return new CozeWorkflowResult(true, body, message);
        }

        static CozeWorkflowResult failure(String message) {
            return new CozeWorkflowResult(false, null, message);
        }
    }
}
