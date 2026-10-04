package ru.nstu.system.event.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lifecycle status of a queue entry (spec "Статусы записи"; design.md D15, D17).
 *
 * <ul>
 *   <li>{@link #WAITING} — active entry, eligible for "next";</li>
 *   <li>{@link #PAUSED} — active entry frozen at its position, skipped by "next";</li>
 *   <li>{@link #PASSED} — surrendered entry, immutable journal row.</li>
 * </ul>
 *
 * <p>{@code WAITING} and {@code PAUSED} are the <em>active</em> statuses covered by
 * the partial unique indexes and the entry limit; {@link #PASSED} is history.</p>
 */
public enum QueueEntryStatus {

    WAITING("WAITING"),

    PAUSED("PAUSED"),

    PASSED("PASSED");

    private final String code;

    QueueEntryStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /** @return {@code true} for {@code WAITING}/{@code PAUSED} */
    public boolean isActive() {
        return this != PASSED;
    }

    @JsonCreator
    public static QueueEntryStatus fromCode(String value) {
        if (value == null) {
            return null;
        }
        for (QueueEntryStatus status : values()) {
            if (status.code.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unsupported queue entry status: " + value);
    }
}
