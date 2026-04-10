package top.fusb.huatuo.dx.agent;

import top.fusb.huatuo.dx.agent.config.HuatuoAgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(HuatuoAgentProperties.class)
public class HuatuoDxAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(HuatuoDxAgentApplication.class, args);
    }
}
