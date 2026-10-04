package ru.nstu.system.event.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lifecycle status of an event (spec "Открытие и закрытие события").
 *
 * <ul>
 *   <li>{@link #OPEN} — the queue accepts new entries;</li>
 *   <li>{@link #CLOSED} — read-only, the retention countdown runs;</li>
 *   <li>{@link #ARCHIVED} — compressed history, invisible in active lists and
 *       by short link (archiving itself belongs to group 9).</li>
 * </ul>
 */
public enum EventStatus {

    OPEN("OPEN"),

    CLOSED("CLOSED"),

    ARCHIVED("ARCHIVED");

    private final String code;

    EventStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static EventStatus fromCode(String value) {
        if (value == null) {
            return null;
        }
        for (EventStatus status : values()) {
            if (status.code.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unsupported event status: " + value);
    }
}
