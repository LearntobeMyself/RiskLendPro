package org.example.risklendpro.entity.credit;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户外部特征实体类 - 从credit_data_db数据库读取
 */
@Data
public class UserExternalFeatures {
    private Long id;
    private Long userId;
    private String idCard;
    
    // 征信相关
    private Integer creditScore;
    private Integer overdueCount12m;
    private Integer creditQueryCount3m;
    
    // 多头借贷相关
    private Integer multiHeadLoanCount;
    private BigDecimal multiHeadLoanTotalAmount;
    
    // 负债收入比
    private Integer dti;
    
    // 反欺诈行为数据
    private Integer deviceIsVirtual;
    private Integer deviceChangeCount30d;
    private Integer ipIsProxy;
    
    // 元数据
    private String dataSource;
    private Date updatedAt;
}
