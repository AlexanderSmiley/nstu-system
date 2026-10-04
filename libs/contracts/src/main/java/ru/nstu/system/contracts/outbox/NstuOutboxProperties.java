package ru.nstu.system.contracts.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration of the transactional outbox (design.md D13).
 *
 * <p>Bound from {@code nstu.outbox.*}:</p>
 * <ul>
 *   <li>{@code schema} — schema owning the {@code outbox} table (required);</li>
 *   <li>{@code batch-size} — rows fetched per poll (default 100);</li>
 *   <li>{@code poll-interval} — scheduler delay, ISO-8601 duration
 *       (default {@code PT5S}); read directly from the environment by
 *       {@link JdbcOutboxPublisher}'s {@code @Scheduled} annotation.</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "nstu.outbox")
public class NstuOutboxProperties {

    public static final int DEFAULT_BATCH_SIZE = 100;

    private String schema;

    private int batchSize = DEFAULT_BATCH_SIZE;

    private Duration pollInterval = Duration.ofSeconds(5);

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }
}
