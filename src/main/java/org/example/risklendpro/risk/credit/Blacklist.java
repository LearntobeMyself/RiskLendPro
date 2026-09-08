package org.example.risklendpro.risk.credit;

import lombok.Data;
import java.util.Date;

/**
 * 失信被执行人黑名单实体类 - 从credit_data_db数据库读取
 * 对应表: blacklist
 */
@Data
public class Blacklist {
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 被执行人姓名/名称（匹配第一维度）
     */
    private String name;
    
    /**
     * 地区编码（由执行法院转换，匹配第二维度）
     */
    private String areaCode;
    
    /**
     * 出生年份（从出生日期提取，匹配第三维度）
     */
    private Integer birthYear;
    
    /**
     * 案号（人工审批时核对具体案件）
     */
    private String caseNo;
    
    /**
     * 执行法院（辅助展示）
     */
    private String courtName;
    
    /**
     * 被执行人履行情况（判定风险严重程度）
     */
    private String dutyStatus;
    
    /**
     * 失信被执行人行为情况（具体原因）
     */
    private String behaviorDetails;
    
    /**
     * 风险等级（HIGH/MEDIUM/LOW）
     */
    private String riskLevel;
    
    /**
     * 数据创建时间
     */
    private Date createdAt;
    
    /**
     * 过期时间（NULL=永久）
     */
    private Date expireAt;
}