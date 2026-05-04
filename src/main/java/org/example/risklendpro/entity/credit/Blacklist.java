package org.example.risklendpro.entity.credit;

import lombok.Data;
import java.util.Date;

/**
 * 黑名单实体类 - 从credit_data_db数据库读取
 */
@Data
public class Blacklist {
    private Long id;
    private String idCard;
    private String phone;
    private String reason;
    private String source;
    private Date createdAt;
    private Date expireAt;
}
