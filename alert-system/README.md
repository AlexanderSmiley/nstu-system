# alert-system (контракт, не реализовано в MVP)

Сервис оповещений персонала: следит за доменными событиями и рассылает
предупреждения (например, «очередь события закрывается, осталось N несдавших»).
В MVP **кода нет** — это папка с контрактом (design.md D22, `proposal.md`
Non-goals). Настоящий `notification-service` — заглушка: он подписан на все события
и только логирует их, не рассылая уведомления.

## Границы сервиса

- Отвечает за: правила оповещений (какое событие кому и когда отправлять), историю
  отправок, шаблоны сообщений, анти-спам/дедупликацию отправок.
- Владеет данными: собственные таблицы (правила, подписки, журнал отправок) в
  отдельной схеме БД. ПИИ не хранит — только `accountId`/`eventId`.
- Не владеет доменными данными: события, очередь, аккаунты, профили принадлежат
  `event-service`, `auth-service`, `student-service`.
- Не публикует доменные события и не пишет в чужие схемы; взаимодействие только
  чтение входящих событий из RabbitMQ.
- Не заменяет `notification-service` (тот — транспортная заглушка/лог), а
  подключается самостоятельным потребителем. Каналы доставки (email, Telegram) —
  вне MVP; будущий `telegram-bot` может быть отдельным каналом.

## Подписка на `nstu.events`

Exchange `nstu.events` (topic). Интересующие события:

| Routing key | Почему интересно |
| --- | --- |
| `event.closed` | напомнить о незавершённых записях/архивации |
| `entry.passed` | лог/дайджест сдач |
| `queue.advanced` | кто прошёл, кто следующий |
| `event.archived` | завершение жизненного цикла события |
| `account.created` | регистрация нового получателя/связи (не критично в MVP) |
| `account.updated` | смена роли/имени получателя (не критично в MVP) |
| `account.blocked` | оповестить персонал о блокировке |
| `account.unblocked` | снять предупреждение |
| `account.password_reset` | событие безопасности аккаунта |
| `profile.updated` | актуализация ФИО в правилах/шаблонах |

Общий конверт: `eventId`, `eventType`, `version`, `occurredAt`, `payload`
(см. `libs/contracts/src/main/java/ru/nstu/system/contracts/events`).

## Точка подключения

- Своя durable-очередь:
  `NstuQueueNames.forService("<service-name>")` → `nstu.events.<service-name>`.
  Имя выбирается при реализации (например, `alert`), после чего совпадает с
  `docker-compose` и healthcheck'ами.
- Очередь объявляется с `x-dead-letter-exchange = RabbitNames.DLX`
  (`nstu.events.dlx`) и биндится к `RabbitNames.EXCHANGE` (`nstu.events`) с
  паттерном `#`, если нужны все события, либо с точным набором routing key.
- Повторная обработка: retry с backoff из `spring.rabbitmq.listener.simple.retry.*`,
  затем DLQ `nstu.events.dlq` и переброс планировщиком (`nstu.dlq.redrive-interval`).
- Идемпотентность по `eventId`: `IdempotencyHandler` + `IdempotencyGuard`
  (`JdbcIdempotencyGuard` с таблицей `processed_event` в своей схеме — сервис
  хранит состояние, в отличие от заглушки `notification-service`).
- Общие RabbitMQ-настройки — `libs/contracts` (`NstuRabbitConfig`, `EventPublisher`).

## Что НЕ реализовано в MVP

- Нет кода сервиса, схемы БД, миграций и очереди потребителя.
- Нет правил оповещений, шаблонов и каналов доставки (email/SMTP, Telegram).
- Нет истории/дедупликации отправок и анти-спама.
- Нет административного API управления подписками.
- `notification-service` остаётся логирующей заглушкой и ничего не рассылает.
