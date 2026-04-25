package org.example.risklendpro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("mock_data")
public class MockData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String idCard;
    @TableField("is_blacklist")
    private Boolean isBlacklist;
    @TableField("overdue_count")
    private Integer overdueCount;
    @TableField("loan_count")
    private Integer loanCount;
    @TableField("recent_query_count")
    private Integer recentQueryCount;
    @TableField("create_time")
    private Date createTime;
    @TableField("update_time")
    private Date updateTime;
}
