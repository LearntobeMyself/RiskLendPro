package org.example.risklendpro.loan.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_product_event")
public class UserProductEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long productId;
    private String eventType;
    private String scene;
    private String sessionId;
    private String keyword;
    private String metadata;
    private String requestId;
    private String dedupeKey;
    private LocalDateTime createdAt;
}
