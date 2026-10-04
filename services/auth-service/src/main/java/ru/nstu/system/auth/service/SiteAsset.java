package ru.nstu.system.auth.service;

import java.time.Instant;
import java.util.UUID;

/**
 * A stored site asset (change add-site-icon).
 *
 * @param kind        asset kind; {@link SiteAssetService#ICON_KIND} for the icon
 * @param contentType detected MIME type (never trusted from the client)
 * @param bytes       raw file content
 * @param sizeBytes   length of {@code bytes}
 * @param updatedAt   when the asset was last written
 * @param updatedBy   administrator who last wrote it, or {@code null}
 */
public record SiteAsset(
        String kind,
        String contentType,
        byte[] bytes,
        int sizeBytes,
        Instant updatedAt,
        UUID updatedBy) {
}
