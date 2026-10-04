package ru.nstu.system.event.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * gzip codec for the {@code event.archive_payload} column (design.md D19).
 *
 * <p>Archiving serialises the queue and journal to JSON and compresses it with
 * gzip; restoring reverses the operation. Keeping the codec in one stateless
 * utility makes both directions provably symmetric and testable on their own.</p>
 *
 * <p>Decompression is bounded by {@link #MAX_DECOMPRESSED_BYTES}: a crafted or
 * corrupted payload cannot inflate without limit and exhaust the heap. Exceeding
 * the bound fails fast with an {@link IllegalStateException} instead of an
 * {@link OutOfMemoryError}.</p>
 */
public final class ArchiveCodec {

    /** Upper bound of a decompressed archive payload (64 MiB). */
    static final int MAX_DECOMPRESSED_BYTES = 64 * 1024 * 1024;

    private static final int READ_BUFFER_SIZE = 8 * 1024;

    private ArchiveCodec() {
    }

    /** @return {@code gzip(raw)} */
    public static byte[] gzip(byte[] raw) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.max(32, raw.length / 2));
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
            gzip.write(raw);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot gzip archive payload", ex);
        }
        return buffer.toByteArray();
    }

    /** @return the decompressed input, bounded by {@link #MAX_DECOMPRESSED_BYTES} */
    public static byte[] gunzip(byte[] compressed) {
        return gunzip(compressed, MAX_DECOMPRESSED_BYTES);
    }

    /**
     * Decompresses {@code compressed} while never keeping more than {@code maxBytes}
     * in memory.
     *
     * @param compressed gzip payload (must not be {@code null})
     * @param maxBytes   maximum accepted size of the decompressed data
     * @return the decompressed input
     * @throws IllegalStateException if the payload is corrupt or inflates beyond
     *                               {@code maxBytes}
     */
    static byte[] gunzip(byte[] compressed, int maxBytes) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(
                Math.min(Math.max(32, compressed.length * 4), maxBytes));
        byte[] chunk = new byte[READ_BUFFER_SIZE];
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            int total = 0;
            int read;
            while ((read = gzip.read(chunk)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IllegalStateException(
                            "decompressed archive payload exceeds the limit of " + maxBytes + " bytes");
                }
                buffer.write(chunk, 0, read);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("cannot gunzip archive payload", ex);
        }
        return buffer.toByteArray();
    }
}
