package org.example.risklendpro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.entity.LimitAdjustLog;
import org.example.risklendpro.entity.UserCreditLimit;
import org.example.risklendpro.mapper.LimitAdjustLogMapper;
import org.example.risklendpro.mapper.UserCreditLimitMapper;
import org.example.risklendpro.pojo.request.LimitAdjustRequest;
import org.example.risklendpro.pojo.response.LimitAdjustResponse;
import org.example.risklendpro.pojo.response.UserCreditLimitResponse;
import org.example.risklendpro.service.UserCreditLimitService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;

@Service
public class UserCreditLimitServiceImpl implements UserCreditLimitService {

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private LimitAdjustLogMapper limitAdjustLogMapper;

    @Override
    public UserCreditLimitResponse getUserCreditLimit(Long userId) {
        QueryWrapper<UserCreditLimit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        UserCreditLimit creditLimit = userCreditLimitMapper.selectOne(queryWrapper);

        if (creditLimit == null) {
            throw new RuntimeException("用户额度信息不存在");
        }

        UserCreditLimitResponse response = new UserCreditLimitResponse();
        response.setUserId(creditLimit.getUserId());
        response.setTotalLimit(creditLimit.getTotalLimit());
        response.setUsedLimit(creditLimit.getUsedLimit());
        response.setRemainingLimit(creditLimit.getRemainingLimit());
        response.setOverdueAmount(creditLimit.getOverdueAmount());
        response.setHasOverdue(creditLimit.getHasOverdue());
        response.setLastUpdateTime(creditLimit.getLastUpdateTime());

        return response;
    }

    @Override
    @Transactional
    public LimitAdjustResponse adjustLimit(LimitAdjustRequest request, Long operatorId) {
        QueryWrapper<UserCreditLimit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", request.getUserId());
        UserCreditLimit creditLimit = userCreditLimitMapper.selectOne(queryWrapper);

        if (creditLimit == null) {
            throw new RuntimeException("用户额度信息不存在");
        }

        BigDecimal oldLimit = creditLimit.getTotalLimit();
        BigDecimal newLimit = request.getNewLimit();
        BigDecimal difference = newLimit.subtract(oldLimit);
        BigDecimal newRemainingLimit = creditLimit.getRemainingLimit().add(difference);
        BigDecimal newUsedLimit = creditLimit.getUsedLimit().subtract(difference);

        if (newRemainingLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("新额度不能小于已用额度");
        }

        creditLimit.setTotalLimit(newLimit);
        creditLimit.setRemainingLimit(newRemainingLimit);
        creditLimit.setUsedLimit(newUsedLimit);
        creditLimit.setLastUpdateTime(new Date());
        userCreditLimitMapper.updateById(creditLimit);

        LimitAdjustLog log = new LimitAdjustLog();
        log.setUserId(request.getUserId());
        log.setOldLimit(oldLimit);
        log.setNewLimit(newLimit);
        log.setReason(request.getReason());
        log.setOperatorId(operatorId);
        log.setAdjustTime(new Date());
        limitAdjustLogMapper.insert(log);

        LimitAdjustResponse response = new LimitAdjustResponse();
        response.setUserId(request.getUserId());
        response.setOldLimit(oldLimit);
        response.setNewLimit(newLimit);
        response.setAdjustTime(new Date());

        return response;
    }
}
