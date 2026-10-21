package org.example.risklendpro.risk.score;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * B 卡规则人工刷新入口（阶段 A1）。
 *
 * <p>规则本身带 TTL，发布新版本后无需重启即可自动生效；本入口用于需要「立即生效」的
 * 运维场景（例如激活回滚到上一稳定版本后立刻切换）。</p>
 *
 * <p>位于 /internal/** 之下，不经过网关对外暴露，仅供服务内运维调用。</p>
 */
@RestController
@RequestMapping("/internal/behavior-scores/rules")
public class BehaviorRuleRefreshController {

    @Autowired
    private BehaviorScoreEngineImpl behaviorScoreEngine;

    /**
     * 忽略 TTL 强制重新读取 active 规则，并返回当前运行时规则信息
     * （版本、内容哈希、加载时间、阈值来源、active 版本数）。
     */
    @PostMapping("/refresh")
    public BehaviorScoreEngineImpl.RuleRuntimeInfo refreshRules() {
        return behaviorScoreEngine.refreshRules();
    }

    /** 只读查看当前规则信息，不触发强制刷新。 */
    @PostMapping("/info")
    public BehaviorScoreEngineImpl.RuleRuntimeInfo ruleInfo() {
        return behaviorScoreEngine.describeActiveRule();
    }
}