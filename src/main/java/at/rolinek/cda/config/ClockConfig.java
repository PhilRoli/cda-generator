package at.rolinek.cda.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {
    /** Injectable time source so time-dependent code can be tested with a fixed clock. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
