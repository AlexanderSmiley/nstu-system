package ru.nstu.system.student.messaging;

import java.util.Objects;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.nstu.system.contracts.events.AccountCreatedPayload;
import ru.nstu.system.contracts.events.DomainEvent;
import ru.nstu.system.contracts.idempotency.IdempotentHandler;
import ru.nstu.system.student.config.StudentMessagingConfig;
import ru.nstu.system.student.service.StudentProfileService;

/**
 * Consumes {@code account.created} and provisions the profile (identity spec
 * "Создание профиля при создании аккаунта", task 6.1).
 *
 * <p>Idempotency is delegated to {@link IdempotentHandler}: a redelivered
 * {@code eventId} is skipped, and a failure is turned into an
 * {@code AmqpRejectAndDontRequeueException} so the message is dead-lettered rather
 * than requeued forever. Together with {@link DlqReDriveScheduler} this yields the
 * "delayed profile creation" behaviour required by task 6.2: the profile is
 * eventually created once the transient failure disappears.</p>
 */
@Component
public class AccountCreatedListener {

    private final IdempotentHandler idempotentHandler;

    private final StudentProfileService profileService;

    public AccountCreatedListener(IdempotentHandler idempotentHandler, StudentProfileService profileService) {
        this.idempotentHandler = Objects.requireNonNull(idempotentHandler, "idempotentHandler");
        this.profileService = Objects.requireNonNull(profileService, "profileService");
    }

    @RabbitListener(
            queues = StudentMessagingConfig.STUDENT_EVENTS_QUEUE,
            containerFactory = "studentRabbitListenerContainerFactory")
    public void onAccountCreated(DomainEvent<AccountCreatedPayload> event) {
        idempotentHandler.handle(event, () -> profileService.createProfileFromAccount(event.payload()));
    }
}
