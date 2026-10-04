package ru.nstu.system.event.service;

import java.util.Locale;

/**
 * Canonical comparison form of a queue-entry name (spec "Вступление в очередь",
 * design.md D15).
 *
 * <p>Names are stored twice: the display snapshot {@code name} (trimmed, as
 * typed) and {@code name_normalized} used for the case-insensitive uniqueness
 * check. Normalisation folds case, trims the ends and collapses any run of
 * whitespace to a single space, so {@code "  Иванов   Иван "} and
 * {@code "иванов иван"} collide as intended.</p>
 */
public final class QueueNameNormalizer {

    /** Inclusive upper bound of a queue-entry name, in characters. */
    public static final int MAX_LENGTH = 120;

    private QueueNameNormalizer() {
    }

    /** @return {@code trim + collapse whitespace + lowercase} of a non-null value */
    public static String normalize(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
