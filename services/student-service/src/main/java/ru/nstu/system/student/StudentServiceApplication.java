package ru.nstu.system.student;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Student-service entry point.
 *
 * <p>Component scan is explicit:</p>
 * <ul>
 *   <li>{@code ru.nstu.system.student} — this service;</li>
 *   <li>{@code ru.nstu.system.security} — shared JWT parser and role hierarchy
 *       (design.md D6, D10);</li>
 *   <li>{@code ru.nstu.system.contracts} — shared RabbitMQ topology/config
 *       ({@code NstuRabbitConfig}) and the {@code @ConfigurationProperties}
 *       records for outbox/idempotency (design.md D13).</li>
 * </ul>
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded because accounts
 * live in {@code auth-service}; without the exclusion Spring Boot would create a
 * default in-memory user and log a generated password (same rationale as
 * {@code auth-service}).</p>
 */
@SpringBootApplication(
        scanBasePackages = {
                "ru.nstu.system.student",
                "ru.nstu.system.security",
                "ru.nstu.system.contracts"
        },
        exclude = UserDetailsServiceAutoConfiguration.class)
public class StudentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentServiceApplication.class, args);
    }
}
