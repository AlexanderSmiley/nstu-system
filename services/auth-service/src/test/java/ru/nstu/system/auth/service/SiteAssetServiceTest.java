package ru.nstu.system.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import ru.nstu.system.auth.web.ApiException;

/**
 * Unit tests for {@link SiteAssetService} content detection and size validation
 * (change add-site-icon, design.md D2). The checks are pure (no database), so
 * they are exercised directly through the package-private helpers used by
 * {@link SiteAssetService#storeIcon}.
 */
class SiteAssetServiceTest {

    private static final int MAX_BYTES = 256 * 1024;

    private static final byte[] PNG = pngBytes();

    private static final byte[] ICO = icoBytes();

    private static final byte[] PDF = "%PDF-1.7\n%% fake\n".getBytes(StandardCharsets.UTF_8);

    @Test
    void detectsPngBySignature() {
        assertThat(SiteAssetService.detectContentType(PNG))
                .isEqualTo(SiteAssetService.CONTENT_TYPE_PNG);
    }

    @Test
    void detectsPngBySignatureOnlyEvenWhenSvgTextFollows() {
        byte[] content = concat(PNG, "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        // PNG wins by signature; the trailing text is not treated as SVG.
        assertThat(SiteAssetService.detectContentType(content))
                .isEqualTo(SiteAssetService.CONTENT_TYPE_PNG);
    }

    @Test
    void detectsIcoBySignature() {
        assertThat(SiteAssetService.detectContentType(ICO))
                .isEqualTo(SiteAssetService.CONTENT_TYPE_ICO);
    }

    @Test
    void detectsSvgFromRootElement() {
        assertThat(SiteAssetService.detectContentType(
                ("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 16 16\">"
                        + "<rect width=\"16\" height=\"16\" fill=\"red\"/></svg>")
                        .getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(SiteAssetService.CONTENT_TYPE_SVG);
    }

    @Test
    void detectsSvgAfterXmlDeclarationAndLeadingWhitespace() {
        assertThat(SiteAssetService.detectContentType(
                ("\n <?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>")
                        .getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(SiteAssetService.CONTENT_TYPE_SVG);
    }

    @Test
    void acceptsSvgAtExactlyTheSizeLimit() {
        assertThatCode(() -> SiteAssetService.checkSize(MAX_BYTES, MAX_BYTES))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsContentLargerThanLimit() {
        assertThatCode(() -> SiteAssetService.checkSize(MAX_BYTES + 1, MAX_BYTES))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(413);
                    assertThat(exception.getCode()).isEqualTo("icon_too_large");
                });
    }

    @Test
    void rejectsContentThatIsNotPngIcoOrSvg() {
        assertThatCode(() -> SiteAssetService.detectContentType(PDF))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("invalid_icon");
                });
    }

    @Test
    void rejectsEmptyContent() {
        assertThatCode(() -> SiteAssetService.detectContentType(new byte[0]))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("invalid_icon");
                });
    }

    @Test
    void rejectsSvgWithScriptTag() {
        rejectsSvg("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>");
    }

    @Test
    void rejectsSvgWithJavascriptUrl() {
        rejectsSvg("<svg xmlns=\"http://www.w3.org/2000/svg\">"
                + "<a href=\"javascript:alert(1)\">x</a></svg>");
    }

    @Test
    void rejectsSvgWithInlineEventHandler() {
        rejectsSvg("<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"></svg>");
    }

    private static void rejectsSvg(String svg) {
        assertThatCode(() -> SiteAssetService.detectContentType(svg.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("invalid_icon");
                });
    }

    private static byte[] pngBytes() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        };
    }

    private static byte[] icoBytes() {
        return new byte[] {0x00, 0x00, 0x01, 0x00, 0x01, 0x00};
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}