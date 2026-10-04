package ru.nstu.system.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nstu.system.auth.config.SiteIconProperties;
import ru.nstu.system.auth.web.ApiException;

/**
 * Site assets backed by {@code auth.site_asset} (change add-site-icon, design.md
 * D1/D2).
 *
 * <p>Only the site icon is supported for now ({@link #ICON_KIND}); the binary
 * content lives in PostgreSQL, so no S3 bucket or shared file volume is needed.
 * The content type is always <em>detected from the bytes</em> (PNG/ICO
 * signatures, SVG text), never taken from the client, and the size limit is
 * enforced here before anything is written. SVG payloads are additionally
 * scanned for script vectors and rejected when any are found.</p>
 */
@Service
public class SiteAssetService {

    /** Primary key of the single supported asset. */
    public static final String ICON_KIND = "icon";

    public static final String CONTENT_TYPE_PNG = "image/png";

    public static final String CONTENT_TYPE_ICO = "image/x-icon";

    public static final String CONTENT_TYPE_SVG = "image/svg+xml";

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final byte[] ICO_SIGNATURE = {
            0x00, 0x00, 0x01, 0x00
    };

    private static final char BOM = '\uFEFF';

    /** Scripts and event handlers that make an SVG executable. */
    private static final Pattern SVG_SCRIPT_TAG = Pattern.compile("(?is)<script\\b");

    private static final Pattern SVG_JAVASCRIPT_URL = Pattern.compile("(?is)javascript:");

    private static final Pattern SVG_EVENT_HANDLER = Pattern.compile("(?i)\\bon[a-z]+\\s*=");

    private static final String UPSERT_SQL =
            "insert into auth.site_asset (kind, content_type, bytes, size_bytes, updated_at, updated_by)"
                    + " values (?, ?, ?, ?, now(), ?)"
                    + " on conflict (kind) do update"
                    + " set content_type = excluded.content_type,"
                    + "     bytes = excluded.bytes,"
                    + "     size_bytes = excluded.size_bytes,"
                    + "     updated_at = excluded.updated_at,"
                    + "     updated_by = excluded.updated_by";

    private final JdbcTemplate jdbcTemplate;

    private final SiteIconProperties properties;

    public SiteAssetService(JdbcTemplate jdbcTemplate, SiteIconProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    /** @return the stored icon, or empty when none has been uploaded yet */
    @Transactional(readOnly = true)
    public Optional<SiteAsset> findIcon() {
        List<SiteAsset> rows = jdbcTemplate.query(
                "select kind, content_type, bytes, size_bytes, updated_at, updated_by"
                        + " from auth.site_asset where kind = ?",
                (resultSet, rowNumber) -> new SiteAsset(
                        resultSet.getString("kind"),
                        resultSet.getString("content_type"),
                        resultSet.getBytes("bytes"),
                        resultSet.getInt("size_bytes"),
                        resultSet.getTimestamp("updated_at").toInstant(),
                        resultSet.getObject("updated_by", UUID.class)),
                ICON_KIND);
        return rows.stream().findFirst();
    }

    /**
     * Validates and stores a new icon, replacing any previous one.
     *
     * @param actorId administrator performing the change (recorded in {@code updated_by})
     * @param content raw uploaded bytes
     * @return the stored asset, with the database timestamp
     * @throws ApiException 400 {@code invalid_icon} for an empty or unsupported
     *                      payload (including a malicious SVG), 413
     *                      {@code icon_too_large} when the limit is exceeded
     */
    @Transactional
    public SiteAsset storeIcon(UUID actorId, byte[] content) {
        if (content == null || content.length == 0) {
            throw invalidIcon("Файл иконки пуст");
        }
        checkSize(content.length, properties.getMaxBytes());
        String contentType = detectContentType(content);
        jdbcTemplate.update(UPSERT_SQL, ICON_KIND, contentType, content, content.length, actorId);
        return findIcon().orElseThrow(() ->
                new IllegalStateException("site asset was not persisted"));
    }

    /**
     * Deletes the stored icon. Idempotent: resetting when no icon exists is a
     * successful no-op.
     */
    @Transactional
    public void resetIcon() {
        jdbcTemplate.update("delete from auth.site_asset where kind = ?", ICON_KIND);
    }

    /**
     * Enforces the configured byte limit.
     *
     * @param size     content length in bytes
     * @param maxBytes configured maximum
     * @throws ApiException 413 {@code icon_too_large} when {@code size > maxBytes}
     */
    static void checkSize(int size, int maxBytes) {
        if (size > maxBytes) {
            throw ApiException.payloadTooLarge("icon_too_large",
                    "Размер иконки не должен превышать " + (maxBytes / 1024) + " КБ");
        }
    }

    /**
     * Detects the content type from the file signature. The client-provided
     * content type and file name are deliberately ignored.
     *
     * @param content raw bytes
     * @return one of {@code image/png}, {@code image/x-icon}, {@code image/svg+xml}
     * @throws ApiException 400 {@code invalid_icon} when no supported signature matches
     */
    static String detectContentType(byte[] content) {
        if (hasPrefix(content, PNG_SIGNATURE)) {
            return CONTENT_TYPE_PNG;
        }
        if (hasPrefix(content, ICO_SIGNATURE)) {
            return CONTENT_TYPE_ICO;
        }
        String text = decodeText(content);
        if (isSvg(text)) {
            if (containsDangerousSvg(text)) {
                throw invalidIcon("SVG содержит недопустимые элементы или ссылки");
            }
            return CONTENT_TYPE_SVG;
        }
        throw invalidIcon("Допустимы только PNG, ICO и SVG");
    }

    private static boolean hasPrefix(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static String decodeText(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        return text.startsWith(String.valueOf(BOM)) ? text.substring(1) : text;
    }

    private static boolean isSvg(String text) {
        String lower = text.stripLeading().toLowerCase(Locale.ROOT);
        if (lower.startsWith("<svg")) {
            return true;
        }
        return lower.startsWith("<?xml") && lower.contains("<svg");
    }

    private static boolean containsDangerousSvg(String text) {
        return SVG_SCRIPT_TAG.matcher(text).find()
                || SVG_JAVASCRIPT_URL.matcher(text).find()
                || SVG_EVENT_HANDLER.matcher(text).find();
    }

    private static ApiException invalidIcon(String message) {
        return ApiException.badRequest("invalid_icon", message);
    }
}
