package ru.nstu.system.event.service;

import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.nstu.system.event.error.ApiException;

/**
 * Synchronous lookup of a participant's display name in {@code student-service}
 * (design.md D12; spec "Привязка записи к участнику").
 *
 * <p>Called only when an account joins without an explicit entry name: the name
 * is needed immediately to freeze the snapshot on the entry, so an event-driven
 * approach would add a visible delay. Error mapping is part of the API contract:</p>
 * <ul>
 *   <li>{@code 404} (no profile, e.g. an administrator) → {@code 400 profile_required};</li>
 *   <li>any other failure — {@code 5xx}, timeout, connection refused → {@code 503 profile_unavailable}.</li>
 * </ul>
 */
@Component
public class StudentProfileClient {

    /** Header guarding {@code /internal/**} in {@code student-service} (design.md D12). */
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private static final Logger log = LoggerFactory.getLogger(StudentProfileClient.class);

    private static final String PROFILE_PATH = "/internal/students/{accountId}";

    private final RestTemplate restTemplate;

    private final String baseUrl;

    private final String internalToken;

    public StudentProfileClient(RestTemplate studentServiceRestTemplate,
                                @Value("${nstu.student-service.url}") String baseUrl,
                                @Value("${nstu.internal.token:}") String internalToken) {
        this.restTemplate = Objects.requireNonNull(studentServiceRestTemplate, "studentServiceRestTemplate");
        String normalizedBaseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.baseUrl = normalizedBaseUrl.endsWith("/")
                ? normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1)
                : normalizedBaseUrl;
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    /**
     * @return the profile's {@code fullName}
     * @throws ApiException {@code 400 profile_required} when there is no profile,
     *                      {@code 503 profile_unavailable} when the service is unreachable
     */
    public String fetchFullName(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        try {
            InternalStudentProfile profile = fetchProfile(accountId);
            if (profile == null || profile.fullName() == null || profile.fullName().isBlank()) {
                throw ApiException.badRequest("profile_required",
                        "Не удалось определить имя участника; укажите имя вручную");
            }
            return profile.fullName().trim();
        } catch (HttpClientErrorException.NotFound ex) {
            throw ApiException.badRequest("profile_required",
                    "У участника нет профиля; укажите имя вручную");
        } catch (RestClientException ex) {
            log.warn("student-service unavailable while resolving account {}: {}", accountId, ex.getMessage());
            throw ApiException.serviceUnavailable("profile_unavailable",
                    "Сервис профилей временно недоступен, попробуйте позже");
        }
    }

    /**
     * Best-effort variant used to snapshot the author's display name on a calendar
     * entry (change add-preferences-and-calendar-ui; design.md D5).
     *
     * <p>Unlike {@link #fetchFullName(UUID)} this never fails the caller: a missing
     * profile ({@code 404}), an unreachable {@code student-service} or an empty
     * name all yield {@code null}. The calendar module treats the name as a
     * convenience, so it stores nothing instead of rejecting the entry — and the
     * queue contract ({@code profile_required}/{@code profile_unavailable}) stays
     * untouched.</p>
     *
     * @return the trimmed {@code fullName} or {@code null} when it cannot be resolved
     */
    public String tryFetchFullName(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        try {
            InternalStudentProfile profile = fetchProfile(accountId);
            if (profile == null || profile.fullName() == null || profile.fullName().isBlank()) {
                return null;
            }
            return profile.fullName().trim();
        } catch (RestClientException ex) {
            log.warn("Could not resolve display name for account {}: {}", accountId, ex.getMessage());
            return null;
        }
    }

    /** Performs the internal lookup; failures propagate as {@link RestClientException}. */
    private InternalStudentProfile fetchProfile(UUID accountId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_TOKEN_HEADER, internalToken);
        return restTemplate.exchange(
                baseUrl + PROFILE_PATH,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                InternalStudentProfile.class,
                accountId).getBody();
    }

    /**
     * Subset of the {@code student-service} response contract that this service
     * needs. Kept local because the two services share contracts only through
     * {@code libs/contracts}, not through Java types.
     */
    public record InternalStudentProfile(UUID accountId, String fullName, UUID groupId) {
    }
}
