package top.fusb.huatuo.dx.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AgentConfig {

    @Bean
    RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}
