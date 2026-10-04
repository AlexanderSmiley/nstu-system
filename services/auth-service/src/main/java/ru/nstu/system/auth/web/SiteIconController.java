package ru.nstu.system.auth.web;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.nstu.system.auth.service.SiteAsset;
import ru.nstu.system.auth.service.SiteAssetService;
import ru.nstu.system.auth.web.dto.SiteIconResponse;
import ru.nstu.system.security.ParsedToken;
import ru.nstu.system.security.SecurityContextSupport;

/**
 * Site icon API (change add-site-icon, design.md D3).
 *
 * <ul>
 *   <li>{@code PUT /api/admin/site/icon} — multipart upload, ADMIN-only (the
 *       {@code /api/admin/**} rule in
 *       {@link ru.nstu.system.auth.config.AuthSecurityConfig}); returns metadata
 *       without the bytes.</li>
 *   <li>{@code DELETE /api/admin/site/icon} — reset, ADMIN-only, idempotent.</li>
 *   <li>{@code GET /api/site/icon} — public so the login and forced
 *       password-change screens can show the icon; honours {@code ETag} /
 *       {@code If-None-Match} and returns {@code 404} when no icon is set.</li>
 * </ul>
 */
@RestController
public class SiteIconController {

    private static final String CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";

    private static final String CONTENT_TYPE_OPTIONS_VALUE = "nosniff";

    private static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";

    private static final String SVG_CONTENT_SECURITY_POLICY = "default-src 'none'";

    private final SiteAssetService siteAssetService;

    public SiteIconController(SiteAssetService siteAssetService) {
        this.siteAssetService = siteAssetService;
    }

    /** ADMIN-only: stores the uploaded icon and returns its metadata. */
    @PutMapping("/api/admin/site/icon")
    public SiteIconResponse uploadIcon(@RequestPart(name = "file", required = false) MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("invalid_icon", "Файл иконки пуст");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw ApiException.badRequest("invalid_icon", "Не удалось прочитать файл иконки");
        }
        return SiteIconResponse.from(siteAssetService.storeIcon(currentAccountId(), content));
    }

    /** ADMIN-only: removes the icon. Idempotent — succeeds even when none is set. */
    @DeleteMapping("/api/admin/site/icon")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetIcon() {
        siteAssetService.resetIcon();
    }

    /** Public: serves the stored icon with caching and hardening headers. */
    @GetMapping("/api/site/icon")
    public ResponseEntity<byte[]> getIcon(
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        SiteAsset asset = siteAssetService.findIcon().orElse(null);
        if (asset == null) {
            return ResponseEntity.notFound().build();
        }

        String etag = etagOf(asset.bytes());
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(etag);
        headers.setCacheControl(CacheControl.noCache().getHeaderValue());
        headers.set(CONTENT_TYPE_OPTIONS_HEADER, CONTENT_TYPE_OPTIONS_VALUE);
        if (SiteAssetService.CONTENT_TYPE_SVG.equals(asset.contentType())) {
            headers.set(CONTENT_SECURITY_POLICY_HEADER, SVG_CONTENT_SECURITY_POLICY);
        }

        if (matches(ifNoneMatch, etag)) {
            return new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED);
        }

        headers.setContentType(MediaType.parseMediaType(asset.contentType()));
        return new ResponseEntity<>(asset.bytes(), headers, HttpStatus.OK);
    }

    private static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        String value = ifNoneMatch.trim();
        return "*".equals(value) || value.equals(etag) || value.contains(etag);
    }

    private static String etagOf(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "\"" + HexFormat.of().formatHex(digest.digest(content)) + "\"";
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is mandated by the JLS; a missing provider is unrecoverable.
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }

    private static UUID currentAccountId() {
        ParsedToken token = SecurityContextSupport.currentToken()
                .orElseThrow(() -> ApiException.unauthorized("unauthorized", "Требуется аутентификация"));
        try {
            return UUID.fromString(token.subject());
        } catch (IllegalArgumentException ex) {
            throw ApiException.unauthorized("unauthorized", "Сессия недействительна");
        }
    }
}
