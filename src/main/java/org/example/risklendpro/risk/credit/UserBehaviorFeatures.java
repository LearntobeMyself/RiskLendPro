package org.example.risklendpro.risk.credit;

import lombok.Data;

import java.util.Date;

@Data
public class UserBehaviorFeatures {
    private Long id;
    private Long skIdCurr;
    private String idCard;
    private String featureJson;
    private String dataSource;
    private Date updatedAt;
}
