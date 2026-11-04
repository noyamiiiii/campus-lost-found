package edu.campus.lostfound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Authentication uses a stateless JWT filter, so Spring Boot's generated default user is unnecessary.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CampusLostFoundApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusLostFoundApplication.class, args);
    }
}

