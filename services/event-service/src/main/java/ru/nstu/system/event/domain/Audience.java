package ru.nstu.system.event.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Who may see a calendar entry (change add-calendar-module; design.md D3).
 *
 * <ul>
 *   <li>{@link #ME} — personal entry, visible to its author only;</li>
 *   <li>{@link #GROUP} — visible to every role of {@code STUDENT} rank and above;</li>
 *   <li>{@link #STAFF} — visible to {@code STAFF} and {@code ADMIN} only.</li>
 * </ul>
 *
 * <p>The wire/database code doubles as the JSON representation through
 * {@link JsonValue}/{@link JsonCreator}, mirroring {@link Availability}.</p>
 */
public enum Audience {

    ME("ME"),

    GROUP("GROUP"),

    STAFF("STAFF");

    private final String code;

    Audience(String code) {
        this.code = code;
    }

    /** @return the persisted/JSON code, e.g. {@code ME} */
    @JsonValue
    public String code() {
        return code;
    }

    /**
     * Parses a code coming from JSON.
     *
     * @param value wire code; {@code null} is accepted and yields {@code null}
     * @throws IllegalArgumentException on an unknown code, which Jackson maps to 400
     */
    @JsonCreator
    public static Audience fromCode(String value) {
        if (value == null) {
            return null;
        }
        for (Audience audience : values()) {
            if (audience.code.equals(value)) {
                return audience;
            }
        }
        throw new IllegalArgumentException("Unsupported audience: " + value);
    }

    /** Parses a value coming from the database (never {@code null} for a NOT NULL column). */
    public static Audience fromDatabase(String value) {
        if (value == null) {
            return null;
        }
        return fromCode(value);
    }
}
