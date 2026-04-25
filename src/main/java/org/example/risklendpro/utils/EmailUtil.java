package org.example.risklendpro.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailUtil {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * 发送简单邮件
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     */
    public void sendSimpleEmail(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }

    /**
     * 发送风控评估结果通知
     * @param userEmail 用户邮箱
     * @param userName 用户名
     * @param status 评估状态
     * @param creditLimit 授信额度
     */
    public void sendRiskAssessmentNotification(String userEmail, String userName, String status, String creditLimit) {
        String subject = "【RiskLendPro】风控评估结果通知";
        String content = "尊敬的" + userName + "先生/女士：\n\n" +
                "您的风控评估已完成，评估结果如下：\n\n" +
                "评估状态：" + status + "\n" +
                "授信额度：" + creditLimit + "元\n\n" +
                "如有疑问，请联系客服。\n\n" +
                "此致\n" +
                "RiskLendPro团队";
        sendSimpleEmail(userEmail, subject, content);
    }

    /**
     * 发送借款成功通知
     * @param userEmail 用户邮箱
     * @param userName 用户名
     * @param loanAmount 借款金额
     */
    public void sendLoanSuccessNotification(String userEmail, String userName, String loanAmount) {
        String subject = "【RiskLendPro】借款成功通知";
        String content = "尊敬的" + userName + "先生/女士：\n\n" +
                "您的借款申请已自动审批通过，详情如下：\n\n" +
                "借款金额：" + loanAmount + "元\n" +
                "状态：已发放\n\n" +
                "资金已转入您的账户，请查收。\n\n" +
                "此致\n" +
                "RiskLendPro团队";
        sendSimpleEmail(userEmail, subject, content);
    }

    /**
     * 发送借款审批通知（给用户）
     * @param userEmail 用户邮箱
     * @param userName 用户名
     * @param loanAmount 借款金额
     * @param remainingLimit 剩余额度
     */
    public void sendLoanApprovalNotification(String userEmail, String userName, String loanAmount, String remainingLimit) {
        String subject = "【RiskLendPro】借款申请审批通知";
        String content = "尊敬的" + userName + "先生/女士：\n\n" +
                "您的借款申请已提交，详情如下：\n\n" +
                "借款金额：" + loanAmount + "元\n" +
                "剩余额度：" + remainingLimit + "元\n" +
                "状态：待审批\n\n" +
                "由于您的借款金额超出剩余额度，需要管理员审批，请关注最新审批情况。\n\n" +
                "此致\n" +
                "RiskLendPro团队";
        sendSimpleEmail(userEmail, subject, content);
    }
}
