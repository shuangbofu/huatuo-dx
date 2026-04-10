package top.fusb.huatuo.dx.agent.service;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class NodeRegistrationState {

    private final AtomicReference<Long> nodeId = new AtomicReference<>();

    public Long getNodeId() {
        return nodeId.get();
    }

    public void setNodeId(Long value) {
        nodeId.set(value);
    }
}
