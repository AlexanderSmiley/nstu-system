package ru.nstu.system.event.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Component;
import ru.nstu.system.event.web.dto.QueueStateResponse;

/**
 * Deterministic ETag for the queue projection (design.md D20; spec "Обновление
 * очереди без перезагрузки").
 *
 * <p>The design document suggested {@code event.updated_at} as the version, but
 * queue mutations deliberately do not touch the event row (they only lock it).
 * Hashing the serialised projection is therefore strictly stronger: the tag
 * changes on any queue, journal or event-status change. The projection is built
 * deterministically (active entries by position, journal by surrender time then
 * id), so equal state yields equal tags.</p>
 *
 * <p>Because the tag covers the exact representation, a staff tag and a student
 * tag over the same event differ whenever their {@code journal} visibility
 * differs — which is required for a correct conditional response.</p>
 */
@Component
public class QueueStateEtag {

    private final ObjectMapper objectMapper;

    public QueueStateEtag(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /** @return a quoted strong validator, e.g. {@code "9f86d0..."} */
    public String etag(QueueStateResponse state) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(state);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            return '"' + HexFormat.of().formatHex(digest) + '"';
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("cannot serialise queue state for ETag", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /**
     * RFC 9110 {@code If-None-Match} comparison for a single strong tag.
     *
     * @param ifNoneMatch raw header value, may be {@code null}
     * @param etag        the current quoted tag
     * @return {@code true} when the client already holds the current representation
     */
    public static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String value = candidate.trim();
            if ("*".equals(value)) {
                return true;
            }
            if (value.startsWith("W/")) {
                value = value.substring(2);
            }
            if (value.equals(etag)) {
                return true;
            }
        }
        return false;
    }
}
