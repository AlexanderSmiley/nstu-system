package ru.nstu.system.event.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

/**
 * Access level of an event (design.md D10, spec "Уровни доступности события").
 *
 * <p>The persisted code ({@code GUEST+}, {@code STUDENT+}, {@code STAFF+}) is not
 * a legal Java identifier, therefore each constant carries its wire/database code
 * explicitly. {@link JsonValue}/{@link JsonCreator} make the code the single
 * representation both in JSON and in the database.</p>
 *
 * <p>{@link #requiredRank()} is only meaningful when compared with a role rank
 * (see {@code EventAccessService}): the higher the rank, the stricter the level.</p>
 */
public enum Availability {

    /** Available to everyone, including anonymous guest sessions. */
    GUEST_PLUS("GUEST+", 1),

    /** Available to students, staff and administrators. */
    STUDENT_PLUS("STUDENT+", 2),

    /** Service event, available to staff and administrators only. */
    STAFF_PLUS("STAFF+", 3);

    private final String code;

    private final int requiredRank;

    Availability(String code, int requiredRank) {
        this.code = code;
        this.requiredRank = requiredRank;
    }

    /** @return the persisted/JSON code, e.g. {@code GUEST+} */
    @JsonValue
    public String code() {
        return code;
    }

    /** @return minimum role rank allowed to open the event */
    public int requiredRank() {
        return requiredRank;
    }

    /**
     * Parses a code coming from JSON.
     *
     * @param value wire code; {@code null} is accepted and yields {@code null}
     * @throws IllegalArgumentException on an unknown code, which Jackson maps to 400
     */
    @JsonCreator
    public static Availability fromCode(String value) {
        if (value == null) {
            return null;
        }
        for (Availability availability : values()) {
            if (availability.code.equals(value)) {
                return availability;
            }
        }
        throw new IllegalArgumentException("Unsupported availability: " + value);
    }

    /**
     * Parses a value coming from the database (never {@code null} for a mapped
     * NOT NULL column). Kept null-tolerant for defensive reads.
     */
    public static Availability fromDatabase(String value) {
        Objects.requireNonNull(value, "value");
        return fromCode(value);
    }
}
