package org.example.risklendpro.risk.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.risk.bcard.BCardFeatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "管理员风控审批", description = "人工复核、风控报告与 B 卡监控")
@RestController
@RequestMapping("/admin")
public class AdminRiskController {

    @Autowired
    private AdminRiskQueryService adminRiskQueryService;

    @Autowired
    private BCardFeatureService bCardFeatureService;

    @Operation(summary = "获取待审批列表", description = "获取所有状态为MANUAL_REVIEW的订单列表")
    @GetMapping("/risk/list")
    public CommonResponse<Object> riskList(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false) String status) {
        return CommonResponse.success("查询成功", adminRiskQueryService.getRiskList(page, size, status));
    }

    @Operation(summary = "获取详细风控报告", description = "获取Python引擎生成的详细JSON报告，包含得分拆解和数据融合比对")
    @GetMapping("/risk/report/{applyId}")
    public CommonResponse<Object> riskReport(@PathVariable String applyId) {
        return CommonResponse.success("查询成功", adminRiskQueryService.getRiskReport(applyId));
    }

    @Operation(summary = "管理员最终审批决策", description = "管理员给出最终贷款额度，审批结果邮件通知用户")
    @PostMapping("/risk/approve")
    public CommonResponse<Void> riskApprove(@RequestBody RiskApproveRequest request) {
        adminRiskQueryService.approveRisk(request);
        return CommonResponse.success(null);
    }

    @Operation(summary = "B 卡贷后监控列表", description = "已启用 B 卡用户的 B 分、还款态势与预警标签")
    @GetMapping("/b-card/monitor")
    public CommonResponse<Object> bCardMonitor() {
        return CommonResponse.success("查询成功", adminRiskQueryService.getBCardMonitor());
    }

    @Operation(summary = "手动重算 B 卡分数", description = "演示或 seed 调整还款日后立即刷新 B 分")
    @PostMapping("/b-card/recalculate/{userId}")
    public CommonResponse<Object> bCardRecalculate(@PathVariable Long userId) {
        return CommonResponse.success("重算成功", adminRiskQueryService.recalculateBCard(userId));
    }

    @Operation(summary = "生成 B 卡 V2 特征快照", description = "按 userId 和当前日期生成真实贷后行为特征快照")
    @PostMapping("/b-card/snapshot/{userId}")
    public CommonResponse<Object> createBCardSnapshot(@PathVariable Long userId) {
        return CommonResponse.success("生成成功", bCardFeatureService.createSnapshot(userId, LocalDate.now()));
    }

    @Operation(summary = "批量生成 B 卡 V2 特征快照", description = "为所有已启用 B 卡的用户生成当前日期快照")
    @PostMapping("/b-card/snapshot-batch")
    public CommonResponse<Object> createAllBCardSnapshots() {
        int count = bCardFeatureService.createSnapshotsForBCardUsers(LocalDate.now());
        return CommonResponse.success("批量生成完成", count);
    }

    @Operation(summary = "查询最新 B 卡 V2 特征快照", description = "查询用户最近一次真实贷后行为特征快照")
    @GetMapping("/b-card/features/{userId}")
    public CommonResponse<Object> getBCardFeatures(@PathVariable Long userId) {
        return CommonResponse.success("查询成功", bCardFeatureService.getLatestSnapshot(userId));
    }
}
