# telegram-bot (контракт, не реализовано в MVP)

Сервис-бот Telegram для группы: дублирует важные уведомления в чат и, в будущем,
позволяет участникам управлять очередью из мессенджера. В MVP **кода нет** — это
папка с контрактом (design.md D22, `proposal.md` Non-goals). Реализация
`notification-service` — отправка в Telegram — намеренно отложена.

## Границы сервиса

- Владеет данными: подписки (кто и куда получает уведомления), привязка
  Telegram-пользователя к `accountId`, состояние диалогов. Собственная схема БД.
- Не владеет доменными данными: события, очередь, аккаунты и профили принадлежат
  `event-service`, `auth-service` и `student-service`. Бот только читает/реагирует.
- Не пишет в чужие схемы и не вызывает внутренние эндпоинты других сервисов напрямую
  (кроме `/internal/**` при явной необходимости и с заголовком `X-Internal-Token`).
- Наружу (через nginx/gateway) не публикует собственный UI API; входящая точка —
  вебхук Telegram.

## Потребляемые доменные события

Подписка — на topic-exchange `nstu.events` (очередь
`NstuQueueNames.forService("<service-name>")`, см. ниже). Типы событий и краткие
payload'ы (`libs/contracts/src/main/java/ru/nstu/system/contracts/events`):

| Routing key | Payload | Смысл |
| --- | --- | --- |
| `account.created` | `accountId`, `username`, `role`, `displayName` | создан аккаунт |
| `account.updated` | `accountId`, `username`, `role`, `displayName` | изменены атрибуты аккаунта |
| `account.blocked` | `accountId` | аккаунт заблокирован |
| `account.unblocked` | `accountId` | аккаунт разблокирован |
| `account.password_reset` | `accountId` | сброшен пароль / отозваны refresh-токены |
| `profile.updated` | `accountId`, `fullName` | изменён профиль (ФИО) |
| `event.closed` | `eventId`, `slug`, `title` | событие закрыто |
| `entry.passed` | `eventId`, `entryId`, `name`, `passedAt`, `passedBy` | запись сдана |
| `queue.advanced` | `eventId`, `passedEntryId`, `passedName`, `nextEntryId` | очередь продвинулась |
| `event.archived` | `eventId` | событие заархивировано |

Общий конверт: `DomainEvent<T>` = `eventId` (UUID), `eventType`,
`version` (`EventTypes.CURRENT_VERSION`), `occurredAt` (ISO-8601), `payload`.
Ссылки на события — через `eventId`, на события-очереди — через `slug`
(`/e/<slug>`).

## Вебхуки Telegram

Telegram не умеет сам определять идемпотентность, поэтому контракт задаёт её на
стороне бота.

### Маршруты

| Метод | Путь | Назначение |
| --- | --- | --- |
| `POST` | `/telegram/webhook` | приём обновлений (`Update`) от Telegram Bot API |
| `GET` | `/telegram/health` | liveness/readiness для docker-compose |

Внешний адрес вебхука задаётся через `setWebhook`:

```
POST https://api.telegram.org/bot<TOKEN>/setWebhook
{
  "url": "https://<host>/telegram/webhook",
  "secret_token": "<TELEGRAM_WEBHOOK_SECRET>",
  "allowed_updates": ["message", "callback_query"]
}
```

### Формат запроса

Тело — стандартный Telegram `Update` (JSON), например:

```json
{
  "update_id": 123456789,
  "message": {
    "message_id": 42,
    "from": { "id": 100200300, "username": "student" },
    "chat": { "id": -1001234567890, "type": "group" },
    "text": "/status lab-1"
  }
}
```

Ответ: `200 OK` без тела — Telegram повторяет доставку при любом другом коде, поэтому
бот обязан быть идемпотентным, а не «отвечать» через тело вебхука. Исходящие сообщения
отправляются отдельными вызовами `sendMessage`.

### Требования к безопасности

- **Обязательный секретный токен**: каждый входящий запрос должен содержать
  заголовок `X-Telegram-Bot-Api-Secret-Token`, равный значению `secret_token`,
  заданному в `setWebhook`. Запросы без него или с неверным значением отклоняются
  `401/403` и не обрабатываются.
- Секрет хранится только в окружении (`TELEGRAM_WEBHOOK_SECRET`), не в образе и не
  в git. Токен бота (`TELEGRAM_BOT_TOKEN`) — тоже только в окружении.
- Если Telegram в будущем начнёт передавать подпись — проверять её в дополнение к
  токену; сейчас единственный штатный механизм — secret token.
- Вебхук доступен только с адресов Telegram; на уровне nginx допускается ограничение
  по IP-диапазонам `149.154.160.0/20` и `91.108.4.0/22`.

### Идемпотентность

- Telegram гарантирует только at-least-once и может повторить один и тот же `Update`.
  Бот обязан пропускать уже обработанные `update_id` (или `message_id` в рамках чата).
- Доменные события из RabbitMQ приходят с `eventId`; повторная доставка того же
  `eventId` должна обрабатываться ровно один раз (тот же приём, что в
  `libs/contracts` — `IdempotencyGuard`/`IdempotentHandler`, durable-таблица
  `processed_event`).
- Ошибка обработки не должна блокировать следующие обновления: сообщение уходит в
  DLQ (`nstu.events.dlq`), откуда перебрасывается планировщиком.

## Точка подключения

- Своя durable-очередь: `NstuQueueNames.forService("<service-name>")`
  (`nstu.events.<service-name>`).
- Очередь объявляется с `x-dead-letter-exchange = RabbitNames.DLX`
  (`nstu.events.dlx`) и биндится к exchange `RabbitNames.EXCHANGE` (`nstu.events`)
  с нужным паттерном (например, `event.*`, `entry.*`, `queue.*`).
- Общие RabbitMQ-настройки и публикатор — `libs/contracts` (`NstuRabbitConfig`,
  `EventPublisher`); конверт события — `DomainEvent<T>`.

## Что НЕ реализовано в MVP

- Никакого кода бота: нет клиента Telegram API, `setWebhook`, обработчиков команд.
- Нет привязки Telegram-пользователя к аккаунту и управления подписками.
- Нет отправки уведомлений — `notification-service` только логирует события.
- Нет схемы БД бота и очереди его потребителя.
- Нет команд управления очередью из мессенджера.
