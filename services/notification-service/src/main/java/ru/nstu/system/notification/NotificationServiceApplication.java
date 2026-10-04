package ru.nstu.system.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import ru.nstu.system.contracts.messaging.NstuRabbitConfig;

/**
 * Notification-service entry point.
 *
 * <p>A stateless subscriber stub (design.md D22): it owes its messaging topology
 * to the shared {@link NstuRabbitConfig}, imported explicitly so the service does
 * not scan the whole {@code contracts} module (which would drag in the outbox and
 * JDBC idempotency configuration properties a datasource-less service does not
 * need).</p>
 */
@SpringBootApplication
@Import(NstuRabbitConfig.class)
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
