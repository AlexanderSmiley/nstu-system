package ru.nstu.system.event.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Unit a single queue entry represents (design.md D24).
 *
 * <p>{@code BRIGADE} and {@code PERSON} are, in the MVP, a UI label only: the
 * entry name is always free text. The unit may be changed only while the event
 * has no active entries.</p>
 */
public enum EntryUnit {

    BRIGADE("BRIGADE"),

    PERSON("PERSON");

    private final String code;

    EntryUnit(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static EntryUnit fromCode(String value) {
        if (value == null) {
            return null;
        }
        for (EntryUnit unit : values()) {
            if (unit.code.equals(value)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("Unsupported entry unit: " + value);
    }
}
