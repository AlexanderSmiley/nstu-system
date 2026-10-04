package ru.nstu.system.contracts.support;

import java.util.regex.Pattern;

/**
 * Defence-in-depth validation for SQL identifiers assembled at runtime.
 *
 * <p>Schema names come from configuration, never from user input, but they are
 * still interpolated into SQL statements (PostgreSQL does not accept bind
 * parameters for identifiers). Restricting them to a conservative pattern
 * removes any chance of SQL injection through a misconfigured property.</p>
 */
public final class SqlIdentifiers {

    private static final Pattern VALID_IDENTIFIER =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private SqlIdentifiers() {
    }

    /**
     * @throws IllegalArgumentException if the schema is blank or not a plain identifier
     */
    public static String requireValidSchema(String schema) {
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("database schema must not be blank");
        }
        if (!VALID_IDENTIFIER.matcher(schema).matches()) {
            throw new IllegalArgumentException(
                    "invalid database schema name: '" + schema + "'");
        }
        return schema;
    }

    /** @return {@code <schema>.<table>} after validating the schema name */
    public static String qualify(String schema, String table) {
        return requireValidSchema(schema) + "." + table;
    }
}
