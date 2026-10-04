package ru.nstu.system.contracts.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration of durable consumer idempotency.
 *
 * <p>Bound from {@code nstu.idempotency.schema}: the schema owning the
 * {@code processed_event} table. The table itself is created by the consuming
 * service's Flyway migration; a ready-made DDL fragment ships in
 * {@code db/processed_event.sql}.</p>
 */
@Component
@ConfigurationProperties(prefix = "nstu.idempotency")
public class NstuIdempotencyProperties {

    private String schema;

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }
}
