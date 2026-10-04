package ru.nstu.system.student.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration of the personal notes module (change add-notes-module, design.md
 * D3/D5).
 *
 * <p>Bound from {@code nstu.notes}:</p>
 * <ul>
 *   <li>{@code quota-bytes} — total attachment storage per account (default
 *       104 857 600 = 100 MiB);</li>
 *   <li>{@code max-attachment-bytes} — size of a single file (default
 *       26 214 400 = 25 MiB).</li>
 * </ul>
 *
 * <p>Both limits are enforced in the service layer so the client always gets the
 * precise {@code note_quota_exceeded} / {@code attachment_too_large} code; the
 * servlet multipart ceilings in {@code application.yml} are only a coarse guard
 * that must stay above {@code max-attachment-bytes}.</p>
 */
@Component
@ConfigurationProperties(prefix = "nstu.notes")
public class NotesProperties {

    /** Total attachment bytes allowed per account. */
    private long quotaBytes = 104_857_600L;

    /** Maximum size of a single attachment. */
    private long maxAttachmentBytes = 26_214_400L;

    public long getQuotaBytes() {
        return quotaBytes;
    }

    public void setQuotaBytes(long quotaBytes) {
        this.quotaBytes = quotaBytes;
    }

    public long getMaxAttachmentBytes() {
        return maxAttachmentBytes;
    }

    public void setMaxAttachmentBytes(long maxAttachmentBytes) {
        this.maxAttachmentBytes = maxAttachmentBytes;
    }
}
