package ru.nstu.system.e2e.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

/**
 * Immutable view over an HTTP response produced by {@link ApiClient}.
 *
 * @param status  HTTP status code
 * @param body    response body as text (possibly empty)
 * @param headers response headers; keys are matched case-insensitively by the JDK
 */
public record ApiResponse(int status, String body, Map<String, List<String>> headers) {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    /** @return the body parsed as a JSON tree */
    public JsonNode json() {
        try {
            return JSON.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("response body is not valid JSON: " + body, ex);
        }
    }

    /** @return the first value of the header, or {@code null} */
    public String header(String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    @Override
    public String toString() {
        return "HTTP " + status + (body.isEmpty() ? "" : " " + body);
    }
}
