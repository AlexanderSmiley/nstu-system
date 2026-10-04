package ru.nstu.system.event.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a queue entry came into existence (design.md D15, D18).
 *
 * <p>Group 8 only ever creates {@link #JOIN} entries; {@link #STAFF} and
 * {@link #CARRY_OVER} belong to the carry-over / manual-add tasks (group 9) but
 * are part of the persisted contract, so the codec must understand all three.</p>
 */
public enum QueueOrigin {

    JOIN("JOIN"),

    STAFF("STAFF"),

    CARRY_OVER("CARRY_OVER");

    private final String code;

    QueueOrigin(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static QueueOrigin fromCode(String value) {
        if (value == null) {
            return null;
        }
        for (QueueOrigin origin : values()) {
            if (origin.code.equals(value)) {
                return origin;
            }
        }
        throw new IllegalArgumentException("Unsupported queue entry origin: " + value);
    }
}
