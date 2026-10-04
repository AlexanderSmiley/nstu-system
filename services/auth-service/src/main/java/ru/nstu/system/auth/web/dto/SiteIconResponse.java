package ru.nstu.system.auth.web.dto;

import java.time.Instant;
import java.util.UUID;
import ru.nstu.system.auth.service.SiteAsset;

/**
 * Metadata of the stored site icon, without the bytes themselves (change
 * add-site-icon, design.md D3).
 *
 * @param contentType detected MIME type
 * @param sizeBytes   size in bytes
 * @param updatedAt   when the icon was last written
 * @param updatedBy   administrator who last wrote it, or {@code null}
 */
public record SiteIconResponse(
        String contentType,
        int sizeBytes,
        Instant updatedAt,
        UUID updatedBy) {

    public static SiteIconResponse from(SiteAsset asset) {
        return new SiteIconResponse(
                asset.contentType(),
                asset.sizeBytes(),
                asset.updatedAt(),
                asset.updatedBy());
    }
}
