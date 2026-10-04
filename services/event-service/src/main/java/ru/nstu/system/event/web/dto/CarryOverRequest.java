package ru.nstu.system.event.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /api/events/{id}/carry-over} (task 9.2; spec "Хвост —
 * перенос непрошедших в новое событие"; design.md D18).
 *
 * <p>{@code {id}} is the target event; {@code sourceEventId} is the event the
 * entries are carried from. When {@code entryIds} is absent or empty every active
 * entry of the source is carried; otherwise exactly the listed sources are, and a
 * listed entry that does not belong to the source or is not active is a 400.</p>
 *
 * @param sourceEventId event to carry active entries from
 * @param entryIds      optional selection of source entry ids; {@code null}/empty means "all"
 */
public record CarryOverRequest(
        @NotNull(message = "Исходное событие обязательно") UUID sourceEventId,
        List<UUID> entryIds) {
}
