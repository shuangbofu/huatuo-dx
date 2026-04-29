package top.fusb.huatuo.dx.manager.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(ManagerProperties.class)
public class ManagerConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public ManagerConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Bean
    RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/plugin/log-console/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders(
                        "Content-Type",
                        "X-Huatuo-Access-Key",
                        "X-Huatuo-Timestamp",
                        "X-Huatuo-Nonce",
                        "X-Huatuo-Signature"
                )
                .exposedHeaders("Content-Type", "Cache-Control")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
