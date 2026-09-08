package org.example.risklendpro.risk.credit;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户外部行为特征实体类 - 从credit_data_db数据库读取
 * 对应表: user_external_features
 * 数据来源: Home Credit
 */
@Data
public class UserExternalFeatures {
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * Home Credit 申请 ID（SK_ID_CURR）
     */
    private Long skIdCurr;

    /**
     * 演示用身份证号（与主库 user.id_card 一致，Java 优先按此关联外部特征）
     */
    private String idCard;
    
    /**
     * 出生日期天数（负数，验真：核对年龄）
     */
    private Integer daysBirth;
    
    /**
     * 入职天数（负数，验真：核对工作年限）
     */
    private Integer daysEmployed;
    
    /**
     * 后台记录收入（验真：核实收入）
     */
    private BigDecimal amtIncomeTotal;
    
    /**
     * 近1周征信查询次数（评分：评估多头风险）
     */
    private Integer creditBureauWeek;
    
    /**
     * 近1月征信查询次数（评分：评估多头风险）
     */
    private Integer creditBureauMon;
    
    /**
     * 手机换号天数（评分：评估稳定性）
     */
    private Integer daysLastPhoneChange;
    
    /**
     * 活跃贷款数（评分/验真：负债水平）
     */
    private Integer activeLoansCount;
    
    /**
     * 第三方评分A（评分：权重极高）
     */
    private BigDecimal extSource2;
    
    /**
     * 第三方评分B（评分：补充权威评价）
     */
    private BigDecimal extSource3;
    
    /**
     * 是否有车（0=否，1=是，验真：核实资产）
     */
    private Integer flagOwnCar;

    /** 性别男=1（HC/WOE 回测） */
    private Integer genderMale;

    /** 已婚=1（HC/WOE 回测） */
    private Integer married;

    /** 有房=1（HC/WOE 回测） */
    private Integer ownRealty;

    /** 稳定就业=1（HC/WOE） */
    private Integer employmentStable;

    /** 授信收入比（WOE） */
    private BigDecimal creditIncomeRatio;

    /** 信用卡使用代理（WOE） */
    private BigDecimal ccUtilization;

    /** 逾期次数代理（WOE） */
    private Integer loanOverdueMax6m;
    
    /**
     * 职业类型（评分：职业风险分级）
     */
    private String occupationType;
    
    /**
     * 学历（验真：核实背景）
     */
    private String educationType;
    
    /**
     * 历史标签（0=正常，1=逾期，回测：验证模型）
     */
    private Integer target;
    
    /**
     * 历史被拒次数（拦截：严重风险则拒绝）
     */
    private Integer prevRefusedCount;
    
    /**
     * 数据来源
     */
    private String dataSource;
    
    /**
     * 最后更新时间
     */
    private Date updatedAt;
}