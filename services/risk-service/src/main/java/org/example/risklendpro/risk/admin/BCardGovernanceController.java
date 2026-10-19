package org.example.risklendpro.risk.admin;

import org.example.risklendpro.risk.bcard.BCardModelRegistryService;
import org.example.risklendpro.risk.bcard.BCardModelRegistryStore;
import org.example.risklendpro.risk.bcard.BCardMonitorMetrics;
import org.example.risklendpro.risk.bcard.BCardMonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * B 卡治理接口（阶段 A5）：模型注册 / 人工审批 / 激活 / 回滚、监控告警闭环。
 *
 * <p>位于 /admin/** 之下，需要管理员 JWT。任何操作都不会删除历史版本。</p>
 */
@RestController
@RequestMapping("/admin/b-card/governance")
public class BCardGovernanceController {

    @Autowired
    private BCardModelRegistryService modelRegistryService;

    @Autowired
    private BCardMonitorService monitorService;

    /** 注册候选模型（不激活）。 */
    @PostMapping("/models")
    public BCardModelRegistryStore.ModelRecord register(@RequestBody RegisterModelRequest request) {
        return modelRegistryService.registerCandidate(
                request.modelVersion(), request.featureVersion(), request.labelVersion(),
                request.policyVersion(), request.artifactJson(), request.metricsJson());
    }

    @PostMapping("/models/{version}/approve")
    public BCardModelRegistryStore.ModelRecord approve(@PathVariable("version") String version,
                                                       @RequestParam(value = "actor", required = false) String actor) {
        return modelRegistryService.approve(version, actor);
    }

    @PostMapping("/models/{version}/activate")
    public BCardModelRegistryStore.ModelRecord activate(@PathVariable("version") String version,
                                                        @RequestParam(value = "actor", required = false) String actor) {
        return modelRegistryService.activate(version, actor);
    }

    /** 回滚到曾激活过的稳定版本（历史完整保留）。 */
    @PostMapping("/models/{version}/rollback")
    public BCardModelRegistryStore.ModelRecord rollback(@PathVariable("version") String version,
                                                        @RequestParam(value = "actor", required = false) String actor) {
        return modelRegistryService.rollbackTo(version, actor);
    }

    @GetMapping("/models")
    public List<BCardModelRegistryStore.ModelRecord> listModels() {
        return modelRegistryService.listVersions();
    }

    @GetMapping("/models/active")
    public BCardModelRegistryStore.ModelRecord activeModel() {
        return modelRegistryService.getActive().orElse(null);
    }

    @GetMapping("/alerts")
    public List<Map<String, Object>> alerts(
            @RequestParam(value = "status", required = false, defaultValue = "OPEN") String status) {
        return monitorService.listAlerts(status);
    }

    @PostMapping("/alerts/{id}/ack")
    public Map<String, Object> acknowledge(@PathVariable("id") long id,
                                           @RequestParam(value = "actor", required = false) String actor) {
        return Map.of("updated", monitorService.acknowledge(id, actor));
    }

    @PostMapping("/alerts/{id}/close")
    public Map<String, Object> close(@PathVariable("id") long id,
                                     @RequestParam(value = "actor", required = false) String actor) {
        return Map.of("updated", monitorService.close(id, actor));
    }

    /** 手动触发一次监控（用于排障/回放）。 */
    @PostMapping("/monitor/run")
    public List<BCardMonitorMetrics.AlertDecision> runMonitor(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return monitorService.runDaily(date == null ? LocalDate.now() : date);
    }

    /** 注册请求体。 */
    public record RegisterModelRequest(
            String modelVersion,
            String featureVersion,
            String labelVersion,
            String policyVersion,
            String artifactJson,
            String metricsJson
    ) {
    }
}