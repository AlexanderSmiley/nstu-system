package ru.nstu.system.event.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Who may read the surrender journal of an event (spec "Видимость журнала сдач").
 *
 * <p>{@code STAFF} (default) restricts the journal to staff and administrators;
 * {@code EVERYONE} exposes it to everyone who can access the event.</p>
 */
public enum JournalVisibility {

    STAFF("STAFF"),

    EVERYONE("EVERYONE");

    private final String code;

    JournalVisibility(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static JournalVisibility fromCode(String value) {
        if (value == null) {
            return null;
        }
        for (JournalVisibility visibility : values()) {
            if (visibility.code.equals(value)) {
                return visibility;
            }
        }
        throw new IllegalArgumentException("Unsupported journal visibility: " + value);
    }
}
