package ru.nstu.system.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.nstu.system.security.RoleNames;

/**
 * Task 9.2: carrying the queue tail into another event (spec "Хвост — перенос
 * непрошедших в новое событие"; design.md D18).
 */
class QueueCarryOverIntegrationTest extends AbstractEventIntegrationTest {

    @Test
    void carriesAccountBoundAndGuestEntriesWithCarryOverOrigin() throws Exception {
        UUID source = createEvent(staffToken(), Map.of("title", "Источник"));
        UUID target = createEvent(staffToken(), Map.of("title", "Цель"));
        UUID firstAccount = UUID.randomUUID();
        UUID secondAccount = UUID.randomUUID();
        UUID firstSource = joinQueueOk(source, tokenFor(firstAccount, RoleNames.STUDENT), "Бригада 1");
        UUID secondSource = joinQueueOk(source, tokenFor(secondAccount, RoleNames.STUDENT), "Бригада 2");
        UUID guestSource = joinQueueOk(source, guestToken(), "Гость");

        MvcResult result = carryOver(target, staffToken(), Map.of("sourceEventId", source.toString()));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = responseJson(result);
        assertThat(body.get("added").size()).isEqualTo(3);
        assertThat(body.get("skipped").size()).isZero();
        assertThat(sourceEntryIds(body.get("added")))
                .containsExactlyInAnyOrder(firstSource, secondSource, guestSource);

        JsonNode queue = responseJson(getQueue(target, staffToken())).get("queue");
        assertThat(queue.size()).isEqualTo(3);

        JsonNode first = entryByName(queue, "Бригада 1");
        assertThat(first.get("origin").asText()).isEqualTo("CARRY_OVER");
        assertThat(first.get("holderAccountId").asText()).isEqualTo(firstAccount.toString());
        assertThat(first.get("guestRef").isNull()).isTrue();

        JsonNode second = entryByName(queue, "Бригада 2");
        assertThat(second.get("holderAccountId").asText()).isEqualTo(secondAccount.toString());

        JsonNode guest = entryByName(queue, "Гость");
        assertThat(guest.get("origin").asText()).isEqualTo("CARRY_OVER");
        assertThat(guest.get("holderAccountId").isNull()).isTrue();
        assertThat(guest.get("guestRef").isNull()).isTrue();
    }

    @Test
    void onlyListedEntriesAreCarried() throws Exception {
        UUID source = createEvent(staffToken(), Map.of("title", "Источник"));
        UUID target = createEvent(staffToken(), Map.of("title", "Цель"));
        UUID chosen = joinQueueOk(source, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Выбранная");
        joinQueueOk(source, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Оставшаяся");

        MvcResult result = carryOver(target, staffToken(), Map.of(
                "sourceEventId", source.toString(),
                "entryIds", List.of(chosen.toString())));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = responseJson(result);
        assertThat(body.get("added").size()).isEqualTo(1);
        assertThat(body.get("added").get(0).get("sourceEntryId").asText())
                .isEqualTo(chosen.toString());
        assertThat(responseJson(getQueue(target, staffToken())).get("queue").size()).isEqualTo(1);
    }

    @Test
    void nameTakenInTargetIsSkipped() throws Exception {
        UUID source = createEvent(staffToken(), Map.of("title", "Источник"));
        UUID target = createEvent(staffToken(), Map.of("title", "Цель"));
        joinQueueOk(source, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Занятое");
        joinQueueOk(target, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Занятое");

        MvcResult result = carryOver(target, staffToken(), Map.of("sourceEventId", source.toString()));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode skipped = responseJson(result).get("skipped");
        assertThat(skipped.size()).isEqualTo(1);
        assertThat(skipped.get(0).get("name").asText()).isEqualTo("Занятое");
        assertThat(skipped.get(0).get("reason").asText()).isEqualTo("name_taken");
        assertThat(responseJson(getQueue(target, staffToken())).get("queue").size()).isEqualTo(1);
    }

    @Test
    void entriesBeyondTheTargetLimitAreSkipped() throws Exception {
        UUID source = createEvent(staffToken(), Map.of("title", "Источник"));
        UUID target = createEvent(staffToken(), Map.of("title", "Цель", "entryLimit", 1));
        joinQueueOk(source, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Первая");
        joinQueueOk(source, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Вторая");

        MvcResult result = carryOver(target, staffToken(), Map.of("sourceEventId", source.toString()));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = responseJson(result);
        assertThat(body.get("added").size()).isEqualTo(1);
        assertThat(body.get("skipped").size()).isEqualTo(1);
        assertThat(body.get("skipped").get(0).get("reason").asText()).isEqualTo("queue_full");
    }

    @Test
    void closedTargetRejectsCarryOver() throws Exception {
        UUID source = createEvent(staffToken(), Map.of("title", "Источник"));
        UUID target = createEvent(staffToken(), Map.of("title", "Цель"));
        joinQueueOk(source, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Участник");
        assertThat(closeEvent(target, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = carryOver(target, staffToken(), Map.of("sourceEventId", source.toString()));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(result)).isEqualTo("event_closed");
    }

    @Test
    void studentCannotCarryOver() throws Exception {
        UUID source = createEvent(staffToken(), Map.of("title", "Источник"));
        UUID target = createEvent(staffToken(), Map.of("title", "Цель"));

        MvcResult result = carryOver(target, studentToken(), Map.of("sourceEventId", source.toString()));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void passedEntryCannotBeCarriedOver() throws Exception {
        UUID source = createEvent(staffToken(), Map.of("title", "Источник"));
        UUID target = createEvent(staffToken(), Map.of("title", "Цель"));
        UUID passed = joinQueueOk(source, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Сдавший");
        assertThat(advance(source, staffToken()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = carryOver(target, staffToken(), Map.of(
                "sourceEventId", source.toString(),
                "entryIds", List.of(passed.toString())));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_entry");
    }

    @Test
    void foreignEntryIdIsRejected() throws Exception {
        UUID source = createEvent(staffToken(), Map.of("title", "Источник"));
        UUID other = createEvent(staffToken(), Map.of("title", "Другое"));
        UUID target = createEvent(staffToken(), Map.of("title", "Цель"));
        UUID foreign = joinQueueOk(other, tokenFor(UUID.randomUUID(), RoleNames.STUDENT), "Чужая");

        MvcResult result = carryOver(target, staffToken(), Map.of(
                "sourceEventId", source.toString(),
                "entryIds", List.of(foreign.toString())));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("invalid_entry");
    }

    private static JsonNode entryByName(JsonNode queue, String name) {
        for (JsonNode node : queue) {
            if (name.equals(node.get("name").asText())) {
                return node;
            }
        }
        throw new AssertionError("Entry '" + name + "' not found in " + queue);
    }

    private static List<UUID> sourceEntryIds(JsonNode added) {
        return java.util.stream.StreamSupport.stream(added.spliterator(), false)
                .map(node -> UUID.fromString(node.get("sourceEntryId").asText()))
                .toList();
    }
}
