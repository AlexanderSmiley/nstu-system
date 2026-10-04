package ru.nstu.system.event.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Kind of an event; only {@link #QUEUE} exists in the MVP (spec "Создание
 * события").
 *
 * <p>A create request carrying a different code therefore fails during JSON
 * binding and is answered with 400.</p>
 */
public enum EventType {

    QUEUE("QUEUE");

    private final String code;

    EventType(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static EventType fromCode(String value) {
        if (value == null) {
            return null;
        }
        for (EventType type : values()) {
            if (type.code.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported event type: " + value);
    }
}
