package ru.nstu.system.event.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.nstu.system.event.error.ApiException;
import ru.nstu.system.event.service.QueueService;
import ru.nstu.system.event.web.dto.CarryOverRequest;
import ru.nstu.system.event.web.dto.CarryOverResponse;
import ru.nstu.system.event.web.dto.JoinQueueRequest;
import ru.nstu.system.event.web.dto.QueueEntryResponse;
import ru.nstu.system.event.web.dto.QueueStateResponse;
import ru.nstu.system.event.web.dto.ReorderQueueRequest;
import ru.nstu.system.event.web.dto.StaffQueueEntryRequest;
import ru.nstu.system.security.ParsedToken;
import ru.nstu.system.security.SecurityContextSupport;

/**
 * Queue REST API mounted under {@code /api/events/{id}/queue} (tasks 8.1-8.8;
 * design.md D10, D16, D20).
 *
 * <p>Every route requires a valid token (including a guest session); the security
 * filter chain only proves the token, while role-, availability- and
 * state-dependent rules live in {@link QueueService}. Read and mutate responses
 * carry an {@link HttpHeaders#ETAG} computed over the caller-specific projection;
 * {@code GET} honours {@code If-None-Match} with a bodyless 304 (design.md D20).</p>
 */
@RestController
@RequestMapping("/api/events")
public class QueueController {

    private final QueueService queueService;

    private final QueueStateEtag etagCalculator;

    public QueueController(QueueService queueService, QueueStateEtag etagCalculator) {
        this.queueService = queueService;
        this.etagCalculator = etagCalculator;
    }

    /** Joins the queue; an account may omit the name, a guest may not. */
    @PostMapping("/{id}/queue")
    @ResponseStatus(HttpStatus.CREATED)
    public QueueEntryResponse join(@PathVariable UUID id,
                                   @RequestBody(required = false) JoinQueueRequest request) {
        String name = request == null ? null : request.name();
        return QueueEntryResponse.from(queueService.join(id, requireToken(), name));
    }

    /**
     * Staff creates an entry on behalf of a participant ({@code origin = STAFF},
     * no account, no guest session), which occupies the name (task 9.3).
     */
    @PostMapping("/{id}/queue/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public QueueEntryResponse addStaffEntry(
            @PathVariable UUID id,
            @RequestBody(required = false) StaffQueueEntryRequest request) {
        String name = request == null ? null : request.name();
        return QueueEntryResponse.from(queueService.addStaffEntry(id, requireToken(), name));
    }

    /**
     * Carries active entries of a source event into this target event (task 9.2):
     * {@code POST /api/events/{id}/carry-over} where {@code {id}} is the target.
     */
    @PostMapping("/{id}/carry-over")
    public CarryOverResponse carryOver(
            @PathVariable UUID id,
            @Valid @RequestBody CarryOverRequest request) {
        return queueService.carryOver(id, requireToken(), request);
    }

    /** Current queue and (role-dependent) journal, with conditional-GET support. */
    @GetMapping("/{id}/queue")
    public ResponseEntity<QueueStateResponse> state(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        QueueStateResponse state = queueService.state(id, requireToken());
        String etag = etagCalculator.etag(state);
        if (QueueStateEtag.matches(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, etag)
                    .build();
        }
        return ResponseEntity.ok().header(HttpHeaders.ETAG, etag).body(state);
    }

    /** "Next": marks the first waiting entry as passed and returns the new state. */
    @PostMapping("/{id}/queue/advance")
    public ResponseEntity<QueueStateResponse> advance(@PathVariable UUID id) {
        return withEtag(queueService.advance(id, requireToken()));
    }

    /** Suspends a waiting entry without changing its position. */
    @PostMapping("/{id}/queue/{entryId}/pause")
    public ResponseEntity<QueueStateResponse> pause(@PathVariable UUID id, @PathVariable UUID entryId) {
        return withEtag(queueService.pause(id, entryId, requireToken()));
    }

    /** Returns a suspended entry to the queue, keeping its position. */
    @PostMapping("/{id}/queue/{entryId}/resume")
    public ResponseEntity<QueueStateResponse> resume(@PathVariable UUID id, @PathVariable UUID entryId) {
        return withEtag(queueService.resume(id, entryId, requireToken()));
    }

    /** Moves an active entry to another position and renumbers the active queue. */
    @PatchMapping("/{id}/queue/{entryId}/position")
    public ResponseEntity<QueueStateResponse> reorder(
            @PathVariable UUID id,
            @PathVariable UUID entryId,
            @RequestBody ReorderQueueRequest request) {
        return withEtag(queueService.reorder(id, entryId, request.position(), requireToken()));
    }

    /** Staff deletes any active entry; a participant may delete only their own. */
    @DeleteMapping("/{id}/queue/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @PathVariable UUID entryId) {
        queueService.delete(id, entryId, requireToken());
    }

    private ResponseEntity<QueueStateResponse> withEtag(QueueStateResponse state) {
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etagCalculator.etag(state))
                .body(state);
    }

    private static ParsedToken requireToken() {
        return SecurityContextSupport.currentToken()
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Требуется аутентификация"));
    }
}
