package top.fusb.huatuo.dx.agent.service;

import top.fusb.huatuo.dx.agent.config.HuatuoAgentProperties;
import top.fusb.huatuo.dx.agent.exception.BusinessException;
import top.fusb.huatuo.dx.agent.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class InternalAuthService {

    private final HuatuoAgentProperties properties;

    public InternalAuthService(HuatuoAgentProperties properties) {
        this.properties = properties;
    }

    public void verify(String secret) {
        if (!properties.getSecret().equals(secret)) {
            throw new BusinessException(ErrorCode.INVALID_SECRET, "节点密钥无效");
        }
    }
}
