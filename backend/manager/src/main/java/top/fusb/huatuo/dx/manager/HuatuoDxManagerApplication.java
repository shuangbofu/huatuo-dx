package top.fusb.huatuo.dx.manager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class HuatuoDxManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HuatuoDxManagerApplication.class, args);
    }
}
