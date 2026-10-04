package ru.nstu.system.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Event-service entry point.
 *
 * <p>Component scan is explicit:</p>
 * <ul>
 *   <li>{@code ru.nstu.system.event} — this service (domain, web, config);</li>
 *   <li>{@code ru.nstu.system.security} — shared JWT parser and role hierarchy
 *       (design.md D6, D7, D10);</li>
 *   <li>{@code ru.nstu.system.contracts} — shared RabbitMQ topology/config
 *       ({@code NstuRabbitConfig}) and the {@code @ConfigurationProperties}
 *       records for the transactional outbox (design.md D13).</li>
 * </ul>
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded because accounts
 * live in {@code auth-service}; without the exclusion Spring Boot would create a
 * default in-memory user and log a generated password.</p>
 */
@SpringBootApplication(
        scanBasePackages = {
                "ru.nstu.system.event",
                "ru.nstu.system.security",
                "ru.nstu.system.contracts"
        },
        exclude = UserDetailsServiceAutoConfiguration.class)
public class EventServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventServiceApplication.class, args);
    }
}
