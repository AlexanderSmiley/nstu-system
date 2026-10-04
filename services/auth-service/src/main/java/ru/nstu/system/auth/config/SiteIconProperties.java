package ru.nstu.system.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Site icon limits (change add-site-icon, design.md D2).
 *
 * <p>Bound from {@code nstu.site.icon.*}. The byte limit is enforced by the
 * service <em>and</em> mirrored by the servlet multipart ceiling in
 * {@code application.yml}; the servlet ceiling is intentionally higher so the
 * service can answer with the precise {@code icon_too_large} code instead of a
 * container-level error.</p>
 */
@Component
@ConfigurationProperties(prefix = "nstu.site.icon")
public class SiteIconProperties {

    /** 256 KB default limit for an uploaded icon. */
    public static final int DEFAULT_MAX_BYTES = 256 * 1024;

    private int maxBytes = DEFAULT_MAX_BYTES;

    public int getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(int maxBytes) {
        this.maxBytes = maxBytes;
    }
}
