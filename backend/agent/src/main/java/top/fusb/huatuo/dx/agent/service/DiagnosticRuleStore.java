package top.fusb.huatuo.dx.agent.service;

import top.fusb.huatuo.dx.agent.dto.DiagnosticRuleView;
import top.fusb.huatuo.dx.agent.exception.BusinessException;
import top.fusb.huatuo.dx.agent.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class DiagnosticRuleStore {

    private final Map<Long, DiagnosticRuleView> rules = new ConcurrentHashMap<>();

    public void replace(List<DiagnosticRuleView> incomingRules) {
        rules.clear();
        for (DiagnosticRuleView rule : incomingRules) {
            rules.put(rule.id(), rule);
        }
    }

    public DiagnosticRuleView get(Long id) {
        DiagnosticRuleView rule = rules.get(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.RULE_NOT_FOUND, "Agent 上不存在该规则: " + id);
        }
        return rule;
    }

    public List<DiagnosticRuleView> list() {
        return new ArrayList<>(rules.values()).stream()
                .sorted(Comparator.comparing(DiagnosticRuleView::name))
                .toList();
    }
}
