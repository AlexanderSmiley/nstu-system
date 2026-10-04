package ru.nstu.system.notification;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import ru.nstu.system.notification.messaging.LoggingNotificationEventHandler;

/**
 * Replaces the production event handler with the counting/poisonable
 * {@link TestNotificationEventHandler}, while keeping the real logging
 * implementation as its delegate.
 */
@TestConfiguration
class NotificationTestSupport {

    @Bean
    @Primary
    TestNotificationEventHandler testNotificationEventHandler(LoggingNotificationEventHandler delegate) {
        return new TestNotificationEventHandler(delegate);
    }
}
