package ru.nstu.system.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ArchiveCodec}: the gzip round-trip and the bounded
 * decompression that protects the heap from a crafted archive payload (task 9.x
 * hardening).
 */
class ArchiveCodecTest {

    @Test
    void roundTripsPayload() {
        byte[] raw = "{\"queue\":[],\"journal\":\"архив\"}".getBytes(StandardCharsets.UTF_8);

        byte[] restored = ArchiveCodec.gunzip(ArchiveCodec.gzip(raw));

        assertThat(restored).isEqualTo(raw);
    }

    @Test
    void decompressesPayloadExactlyAtTheLimit() {
        byte[] raw = new byte[1024];

        byte[] restored = ArchiveCodec.gunzip(ArchiveCodec.gzip(raw), 1024);

        assertThat(restored).isEqualTo(raw);
    }

    @Test
    void rejectsPayloadExceedingTheLimit() {
        byte[] raw = new byte[4096];
        byte[] compressed = ArchiveCodec.gzip(raw);

        assertThatThrownBy(() -> ArchiveCodec.gunzip(compressed, 1024))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds the limit");
    }
}
