package ru.nstu.system.contracts;

import java.util.UUID;

/**
 * Well-known group identifiers shared between services.
 *
 * <p>The project starts with a single study group. The identifier must be stable
 * across services and across database recreations, therefore it is a fixed
 * constant rather than a generated value. The very same literal is used by the
 * {@code student} Flyway migration that seeds the {@code app_group} row, so the
 * code constant and the database row always stay in sync.</p>
 */
public final class Groups {

    /**
     * Identifier of the single default study group seeded by the
     * {@code student} schema migration.
     *
     * <p>Keep this value in sync with
     * {@code services/student-service/src/main/resources/db/migration/V1__student_core.sql}.</p>
     */
    public static final UUID DEFAULT_GROUP_ID =
            UUID.fromString("592983db-f966-445e-8aca-a099153f78cf");

    private Groups() {
    }
}
