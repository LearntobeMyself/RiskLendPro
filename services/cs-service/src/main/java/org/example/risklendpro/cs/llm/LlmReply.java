package org.example.risklendpro.cs.llm;

import lombok.Data;

/**
 * 大模型一次生成的返回结果（含用于落库的证据字段）。
 */
@Data
public class LlmReply {
    private String content;
    private String model;
    private String requestId;
    private Integer promptTokens;
    private Integer completionTokens;
}
