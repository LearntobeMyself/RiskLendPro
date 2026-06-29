package org.example.risklendpro.service;

import org.example.risklendpro.pojo.request.BatchCreditAdjustRequest;
import org.example.risklendpro.pojo.request.LimitAdjustRequest;

import java.util.Map;

public interface AdminCreditQueryService {

    Map<String, Object> getStats();

    Map<String, Object> listLimits(Integer page, Integer size, String userName, String phone,
                                   Boolean hasOverdue, String status);

    Map<String, Object> adjustLimit(Long userId, LimitAdjustRequest request, Long operatorId);

    Map<String, Object> batchAdjust(BatchCreditAdjustRequest request, Long operatorId);

    Map<String, Object> getAdjustHistory(Long userId, Integer page, Integer size);

    Map<String, Object> getOverdueRules();
}
