package org.example.risklendpro.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.List;

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

    /**
     * 发送借款拒绝通知
     * @param userEmail 用户邮箱
     * @param userName 用户名
     * @param loanAmount 借款金额
     * @param reason 拒绝原因
     */
    public void sendLoanRejectNotification(String userEmail, String userName, String loanAmount, String reason) {
        String subject = "【RiskLendPro】借款申请审批结果通知";
        String content = "尊敬的" + userName + "先生/女士：\n\n" +
                "您的借款申请已处理，详情如下：\n\n" +
                "借款金额：" + loanAmount + "元\n" +
                "状态：已拒绝\n" +
                "拒绝原因：" + reason + "\n\n" +
                "如有疑问，请联系客服。\n\n" +
                "此致\n" +
                "RiskLendPro团队";
        sendSimpleEmail(userEmail, subject, content);
    }

    /**
     * 发送还款提醒通知（到期前3天）
     * @param userEmail 用户邮箱
     * @param userName 用户名
     * @param period 期数
     * @param amount 还款金额
     * @param dueDate 到期日期
     */
    public void sendRepaymentReminderNotification(String userEmail, String userName, int period, String amount, String dueDate) {
        String subject = "【RiskLendPro】还款提醒 - 还款即将到期";
        String content = "尊敬的" + userName + "先生/女士：\n\n" +
                "您的还款即将到期，请提前做好准备：\n\n" +
                "期数：第" + period + "期\n" +
                "还款金额：" + amount + "元\n" +
                "到期日期：" + dueDate + "\n\n" +
                "请确保您的账户余额充足，按时还款以保持良好的信用记录。\n\n" +
                "此致\n" +
                "RiskLendPro团队";
        sendSimpleEmail(userEmail, subject, content);
    }

    /**
     * 发送还款到期提醒（到期当天）
     * @param userEmail 用户邮箱
     * @param userName 用户名
     * @param period 期数
     * @param amount 还款金额
     */
    public void sendRepaymentDueTodayNotification(String userEmail, String userName, int period, String amount) {
        String subject = "【RiskLendPro】还款提醒 - 今日为还款日";
        String content = "尊敬的" + userName + "先生/女士：\n\n" +
                "今天是您的还款日，请尽快完成还款：\n\n" +
                "期数：第" + period + "期\n" +
                "还款金额：" + amount + "元\n\n" +
                "请确保账户余额充足，及时还款避免逾期影响信用记录。\n\n" +
                "此致\n" +
                "RiskLendPro团队";
        sendSimpleEmail(userEmail, subject, content);
    }

    /**
     * 发送逾期通知（逾期第一天）
     * @param userEmail 用户邮箱
     * @param userName 用户名
     * @param period 期数
     * @param overdueDays 逾期天数
     * @param amount 还款金额
     */
    public void sendOverdueNotification(String userEmail, String userName, int period, int overdueDays, String amount) {
        String subject = "【RiskLendPro】重要提醒 - 您的还款已逾期";
        String content = "尊敬的" + userName + "先生/女士：\n\n" +
                "您的还款已发生逾期，请尽快处理：\n\n" +
                "期数：第" + period + "期\n" +
                "逾期天数：" + overdueDays + "天\n" +
                "还款金额：" + amount + "元\n\n" +
                "逾期将会影响您的信用记录，请尽快还款。连续逾期可能导致额度降低或借款功能受限。\n\n" +
                "此致\n" +
                "RiskLendPro团队";
        sendSimpleEmail(userEmail, subject, content);
    }

    /**
     * 人工复核需补充材料通知
     */
    public void sendManualReviewSupplementNotice(String userEmail, String userName,
                                                 List<org.example.risklendpro.pojo.dto.SupplementRequirement> requirements,
                                                 int retentionDays) {
        String subject = "【RiskLendPro】您的授信评估需补充材料";
        StringBuilder sb = new StringBuilder();
        sb.append("尊敬的").append(userName).append("先生/女士：\n\n");
        sb.append("您的授信评估已进入人工复核，请登录 App 在「补充材料」页面上传以下材料：\n\n");
        for (org.example.risklendpro.pojo.dto.SupplementRequirement req : requirements) {
            sb.append("- ").append(req.getLabel());
            if (req.isRequired()) {
                sb.append("（必填）");
            } else {
                sb.append("（可选）");
            }
            sb.append("：").append(req.getDescription()).append("\n");
        }
        sb.append("\n请在 ").append(retentionDays).append(" 天内完成上传，逾期材料将被系统自动清理，需重新提交。\n\n");
        sb.append("此致\nRiskLendPro团队");
        sendSimpleEmail(userEmail, subject, sb.toString());
    }

    /**
     * 补充材料过期提醒
     */
    public void sendSupplementMaterialExpiredNotice(String userEmail, String userName,
                                                    List<org.example.risklendpro.pojo.dto.SupplementRequirement> requirements,
                                                    int retentionDays) {
        String subject = "【RiskLendPro】补充材料已过期，请重新上传";
        StringBuilder sb = new StringBuilder();
        sb.append("尊敬的").append(userName).append("先生/女士：\n\n");
        sb.append("您此前上传的复核材料已超过保留期限（").append(retentionDays).append(" 天），系统已自动清理。\n");
        sb.append("您的评估仍处于人工复核中，请重新登录 App 上传以下材料：\n\n");
        if (requirements != null) {
            for (org.example.risklendpro.pojo.dto.SupplementRequirement req : requirements) {
                sb.append("- ").append(req.getLabel()).append("\n");
            }
        }
        sb.append("\n此致\nRiskLendPro团队");
        sendSimpleEmail(userEmail, subject, sb.toString());
    }
}
