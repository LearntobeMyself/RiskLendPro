package org.example.risklendpro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.entity.MockData;
import org.example.risklendpro.entity.RiskAssessment;
import org.example.risklendpro.entity.User;
import org.example.risklendpro.entity.UserCreditLimit;
import org.example.risklendpro.mapper.MockDataMapper;
import org.example.risklendpro.mapper.RiskAssessmentMapper;
import org.example.risklendpro.mapper.UserCreditLimitMapper;
import org.example.risklendpro.mapper.UserMapper;
import org.example.risklendpro.pojo.request.RiskAssessmentRequest;
import org.example.risklendpro.pojo.response.RiskAssessmentResponse;
import org.example.risklendpro.pojo.response.RiskAssessmentStatusResponse;
import org.example.risklendpro.pojo.response.RiskAssessmentResultResponse;
import org.example.risklendpro.service.RiskAssessmentService;
import org.example.risklendpro.utils.EmailUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RiskAssessmentServiceImpl implements RiskAssessmentService {

    @Autowired
    private RiskAssessmentMapper riskAssessmentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private MockDataMapper mockDataMapper;

    @Autowired
    private EmailUtil emailUtil;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String PYTHON_API_URL = "http://localhost:8000/predict";

    @Override
    @Transactional
    public RiskAssessmentResponse submit(Long userId, RiskAssessmentRequest request) {
        // 1. 通过userId查询用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 2. 检查用户是否已有正在处理中的评估申请
        RiskAssessment existingAssessment = riskAssessmentMapper.selectOne(
                new QueryWrapper<RiskAssessment>()
                        .eq("user_id", userId)
                        .in("status", "WAITING", "MANUAL_REVIEW")
                        .eq("is_final", false)
        );
        if (existingAssessment != null) {
            throw new RuntimeException("您已有正在处理中的评估申请，请等待处理完成");
        }

        // 3. 生成申请ID
        String applyId = generateApplyId();

        // 4. 构建风控评估对象并保存到数据库
        RiskAssessment riskAssessment = new RiskAssessment();
        riskAssessment.setApplyId(applyId);
        riskAssessment.setUserId(userId);
        riskAssessment.setIdCard(request.getIdCard());
        riskAssessment.setName(request.getName());
        riskAssessment.setPhone(request.getPhone());
        riskAssessment.setEmail(request.getEmail());
        riskAssessment.setGender(request.getGender());
        riskAssessment.setBirthday(java.sql.Date.valueOf(request.getBirthday()));
        riskAssessment.setEducation(request.getEducation());
        riskAssessment.setMarriage(request.getMarriage());
        riskAssessment.setJobType(request.getJobType());
        riskAssessment.setMonthlyIncome(request.getMonthlyIncome());
        riskAssessment.setHasHouse(request.getHasHouse());
        riskAssessment.setHasCar(request.getHasCar());
        riskAssessment.setContactPhone(request.getContactPhone());
        riskAssessment.setStatus("WAITING");
        riskAssessment.setSubmitTime(new Date());
        riskAssessment.setIsFinal(false);

        riskAssessmentMapper.insert(riskAssessment);

        // 5. 更新用户评估状态为评估中
        user.setAssessmentStatus("ASSESSING");
        userMapper.updateById(user);

        // 6. 异步调用 Python 进行风控评估
        callPythonRiskAssessment(riskAssessment, request);

        // 7. 构建响应
        RiskAssessmentResponse response = new RiskAssessmentResponse();
        response.setApplyId(applyId);
        response.setSubmitTime(riskAssessment.getSubmitTime());
        response.setStatus(riskAssessment.getStatus());

        return response;
    }

    @Override
    public RiskAssessmentStatusResponse getStatus(String applyId) {
        RiskAssessment riskAssessment = riskAssessmentMapper.selectOne(
                new QueryWrapper<RiskAssessment>().eq("apply_id", applyId)
        );
        if (riskAssessment == null) {
            throw new RuntimeException("评估申请不存在");
        }

        RiskAssessmentStatusResponse response = new RiskAssessmentStatusResponse();
        response.setApplyId(applyId);
        response.setStatus(riskAssessment.getStatus());
        response.setStatusTitle(getStatusTitle(riskAssessment.getStatus()));
        response.setFinal(riskAssessment.getIsFinal());

        return response;
    }

    @Override
    @Transactional
    public RiskAssessmentResultResponse getResult(String applyId) {
        RiskAssessment riskAssessment = riskAssessmentMapper.selectOne(
                new QueryWrapper<RiskAssessment>().eq("apply_id", applyId)
        );
        if (riskAssessment == null) {
            throw new RuntimeException("评估申请不存在");
        }

        if (!riskAssessment.getIsFinal()) {
            throw new RuntimeException("评估尚未完成");
        }

        // 如果评估通过且有额度，创建用户额度记录
        if ("FINAL_PASS".equals(riskAssessment.getStatus()) && riskAssessment.getCreditLimit() != null) {
            UserCreditLimit existingLimit = userCreditLimitMapper.selectOne(
                    new QueryWrapper<UserCreditLimit>().eq("user_id", riskAssessment.getUserId())
            );

            if (existingLimit == null) {
                // 创建新的额度记录
                UserCreditLimit creditLimit = new UserCreditLimit();
                creditLimit.setUserId(riskAssessment.getUserId());
                creditLimit.setTotalLimit(riskAssessment.getCreditLimit());
                creditLimit.setUsedLimit(BigDecimal.ZERO);
                creditLimit.setRemainingLimit(riskAssessment.getCreditLimit());
                creditLimit.setOverdueAmount(BigDecimal.ZERO);
                creditLimit.setHasOverdue(false);
                creditLimit.setLastUpdateTime(new Date());
                userCreditLimitMapper.insert(creditLimit);
            } else {
                // 更新现有额度记录
                existingLimit.setTotalLimit(riskAssessment.getCreditLimit());
                existingLimit.setRemainingLimit(riskAssessment.getCreditLimit().subtract(existingLimit.getUsedLimit()));
                existingLimit.setLastUpdateTime(new Date());
                userCreditLimitMapper.updateById(existingLimit);
            }

            // 更新用户评估状态为已通过
            User user = userMapper.selectById(riskAssessment.getUserId());
            if (user != null) {
                user.setAssessmentStatus("APPROVED");
                userMapper.updateById(user);
            }
        }

        RiskAssessmentResultResponse response = new RiskAssessmentResultResponse();
        response.setApplyId(applyId);
        response.setTotalScore(riskAssessment.getTotalScore());
        response.setCreditLimit(riskAssessment.getCreditLimit());
        response.setExpireDate(riskAssessment.getExpireDate());
        response.setStatus(riskAssessment.getStatus());
        response.setApprovalTime(riskAssessment.getApprovalTime());

        return response;
    }

    /**
     * 异步调用 Python 接口进行风控评估
     */
    @Async
    public void callPythonRiskAssessment(RiskAssessment riskAssessment, RiskAssessmentRequest request) {
        try {
            // 1. 构建请求数据
            Map<String, Object> requestData = new HashMap<>();

            // 用户数据
            Map<String, Object> userData = new HashMap<>();
            userData.put("idCard", request.getIdCard());
            userData.put("name", request.getName());
            userData.put("phone", request.getPhone());
            userData.put("email", request.getEmail());
            userData.put("gender", request.getGender());
            userData.put("birthday", request.getBirthday());
            userData.put("education", request.getEducation());
            userData.put("marriage", request.getMarriage());
            userData.put("jobType", request.getJobType());
            userData.put("monthlyIncome", request.getMonthlyIncome());
            userData.put("hasHouse", request.getHasHouse());
            userData.put("hasCar", request.getHasCar());
            userData.put("contactPhone", request.getContactPhone());

            // 从数据库查询模拟征信数据
            Map<String, Object> mockData = getMockDataFromDatabase(request.getIdCard());

            // 行为数据
            Map<String, Object> behaviorData = new HashMap<>();
            behaviorData.put("applyTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            behaviorData.put("isEmulator", false);

            requestData.put("user_data", userData);
            requestData.put("mock_data", mockData);
            requestData.put("behavior_data", behaviorData);

            // 2. 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestData, headers);

            // 3. 调用 Python 接口
            Map<String, Object> response = restTemplate.postForObject(PYTHON_API_URL, entity, Map.class);

            // 4. 处理响应
            if (response != null) {
                updateAssessmentResult(riskAssessment, response);
            }
        } catch (Exception e) {
            // 处理异常，更新评估状态为失败
            riskAssessment.setStatus("SYSTEM_REJECT");
            riskAssessment.setIsFinal(true);
            riskAssessment.setAuditRemark("风控评估服务异常: " + e.getMessage());
            riskAssessmentMapper.updateById(riskAssessment);
        }
    }

    /**
     * 从数据库查询模拟征信数据
     */
    private Map<String, Object> getMockDataFromDatabase(String idCard) {
        Map<String, Object> mockData = new HashMap<>();
        
        // 根据身份证号查询模拟数据
        MockData data = mockDataMapper.selectOne(
                new QueryWrapper<MockData>().eq("id_card", idCard)
        );
        
        if (data != null) {
            mockData.put("isBlacklist", data.getIsBlacklist());
            mockData.put("overdueCount", data.getOverdueCount());
            mockData.put("loanCount", data.getLoanCount());
            mockData.put("recentQueryCount", data.getRecentQueryCount());
        } else {
            // 如果没有找到模拟数据，使用默认值
            mockData.put("isBlacklist", false);
            mockData.put("overdueCount", 0);
            mockData.put("loanCount", 0);
            mockData.put("recentQueryCount", 0);
        }
        
        return mockData;
    }

    /**
     * 根据 Python 接口返回的结果更新评估记录
     */
    @Transactional
    public void updateAssessmentResult(RiskAssessment riskAssessment, Map<String, Object> pythonResponse) {
        // 解析 Python 响应
        Double totalScore = (Double) pythonResponse.get("total_score");
        String sysDecision = (String) pythonResponse.get("sys_decision");
        Integer creditLimit = (Integer) pythonResponse.get("credit_limit");

        // 更新评估记录
        riskAssessment.setTotalScore(totalScore != null ? totalScore.intValue() : 0);
        riskAssessment.setSysDecision(sysDecision);
        riskAssessment.setApprovalTime(new Date());

        // 根据系统决策设置状态
        String status = "";
        String statusTitle = "";
        String creditLimitStr = "0";

        switch (sysDecision) {
            case "APPROVE":
                riskAssessment.setStatus("FINAL_PASS");
                riskAssessment.setIsFinal(true);
                status = "已通过";
                statusTitle = "评估通过";
                if (creditLimit != null && creditLimit > 0) {
                    riskAssessment.setCreditLimit(new BigDecimal(creditLimit));
                    creditLimitStr = String.valueOf(creditLimit);
                    // 设置额度失效日期（1年后）
                    Calendar calendar = Calendar.getInstance();
                    calendar.add(Calendar.YEAR, 1);
                    riskAssessment.setExpireDate(calendar.getTime());
                }
                break;
            case "REVIEW":
                riskAssessment.setStatus("MANUAL_REVIEW");
                riskAssessment.setIsFinal(false);
                status = "人工复核中";
                statusTitle = "人工复核";
                break;
            case "REJECT":
                riskAssessment.setStatus("SYSTEM_REJECT");
                riskAssessment.setIsFinal(true);
                status = "已拒绝";
                statusTitle = "评估拒绝";
                break;
            default:
                riskAssessment.setStatus("SYSTEM_REJECT");
                riskAssessment.setIsFinal(true);
                status = "已拒绝";
                statusTitle = "评估拒绝";
        }

        riskAssessmentMapper.updateById(riskAssessment);

        // 如果评估完成，更新用户评估状态并发送邮件通知
        if (riskAssessment.getIsFinal()) {
            User user = userMapper.selectById(riskAssessment.getUserId());
            if (user != null) {
                user.setAssessmentStatus("APPROVED".equals(riskAssessment.getStatus()) ? "APPROVED" : "REJECTED");
                userMapper.updateById(user);

                // 发送邮件通知用户
                emailUtil.sendRiskAssessmentNotification(
                        user.getEmail(),
                        user.getRealName(),
                        statusTitle,
                        creditLimitStr
                );
            }
        }
    }

    /**
     * 生成申请ID
     */
    private String generateApplyId() {
        return "L" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4);
    }

    /**
     * 获取状态标题
     */
    private String getStatusTitle(String status) {
        return switch (status) {
            case "WAITING" -> "评估中";
            case "SYSTEM_REJECT" -> "系统拒绝";
            case "MANUAL_REVIEW" -> "人工复核中";
            case "FINAL_PASS" -> "已通过";
            case "FINAL_REJECT" -> "已拒绝";
            default -> "未知状态";
        };
    }
}
