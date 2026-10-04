package ru.nstu.system.e2e.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Minimal HTTP client with a per-session cookie jar (OpenSpec task 12.1).
 *
 * <p>The services authenticate exclusively through httpOnly cookies
 * ({@code access_token}/{@code refresh_token}); tokens never appear in a response
 * body. This client therefore behaves like the SPA: it attaches the cookies it has
 * seen and records every {@code Set-Cookie} it receives.</p>
 *
 * <p>{@link #on(String)} returns a view on the same session that targets another
 * service, so one logical user can call auth-service and event-service with the
 * same cookie jar while each request still goes to the owning service directly
 * (the suite deliberately does not route through the gateway so it also proves
 * that the per-service checks are self-sufficient).</p>
 */
public final class ApiClient {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String baseUrl;

    private final Map<String, String> cookies;

    /** Creates a client with a fresh, empty cookie jar. */
    public ApiClient(String baseUrl) {
        this(baseUrl, new LinkedHashMap<>());
    }

    private ApiClient(String baseUrl, Map<String, String> cookies) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.cookies = cookies;
    }

    /** @return a client for another base URL that shares this session's cookies */
    public ApiClient on(String otherBaseUrl) {
        return new ApiClient(otherBaseUrl, cookies);
    }

    /** @return the current value of the session cookie, or {@code null} */
    public String cookie(String name) {
        return cookies.get(name);
    }

    /** Seeds a cookie, e.g. to replay a token captured earlier in the scenario. */
    public void setCookie(String name, String value) {
        cookies.put(name, value);
    }

    public ApiResponse get(String path) {
        return send("GET", path, null, Map.of());
    }

    public ApiResponse get(String path, Map<String, String> headers) {
        return send("GET", path, null, headers);
    }

    public ApiResponse post(String path, Object body) {
        return send("POST", path, body, Map.of());
    }

    public ApiResponse post(String path, Object body, Map<String, String> headers) {
        return send("POST", path, body, headers);
    }

    public ApiResponse patch(String path, Object body) {
        return send("PATCH", path, body, Map.of());
    }

    public ApiResponse delete(String path) {
        return send("DELETE", path, null, Map.of());
    }

    private ApiResponse send(String method, String path, Object body, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30));

        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookies.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("; ")));
        }
        headers.forEach(builder::header);

        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(serialize(body), StandardCharsets.UTF_8));
        }

        try {
            HttpResponse<String> response = HTTP.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            storeCookies(response);
            return new ApiResponse(response.statusCode(), response.body(), response.headers().map());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(method + " " + baseUrl + path + " interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException(method + " " + baseUrl + path + " failed", ex);
        }
    }

    /** Records every {@code Set-Cookie}; an empty value removes the cookie (logout/clear). */
    private void storeCookies(HttpResponse<String> response) {
        for (String header : response.headers().allValues("Set-Cookie")) {
            int semicolon = header.indexOf(';');
            String nameValue = semicolon < 0 ? header : header.substring(0, semicolon);
            int equals = nameValue.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String name = nameValue.substring(0, equals).trim();
            String value = nameValue.substring(equals + 1).trim();
            if (value.isEmpty()) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
    }

    private static String serialize(Object body) {
        if (body instanceof String text) {
            return text;
        }
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("cannot serialise request body", ex);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
