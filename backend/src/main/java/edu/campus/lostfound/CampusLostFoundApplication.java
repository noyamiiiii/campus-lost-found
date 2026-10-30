package edu.campus.lostfound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * W1 阶段没有真实用户体系，排除默认账号自动配置，避免启动日志出现随机密码。
 * W9 接入 JWT 时移除该 exclude，并配置真正的 UserDetailsService。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CampusLostFoundApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusLostFoundApplication.class, args);
    }
}

