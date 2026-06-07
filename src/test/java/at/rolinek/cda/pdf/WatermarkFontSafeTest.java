package at.rolinek.cda.pdf;

import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the watermark font-safety guard. A watermark text containing a
 * character the standard font cannot encode (e.g. U+009C, which is what a mojibake'd
 * UTF-8 "Ü" decodes to) must NOT throw — it must be sanitized so PDF generation still
 * works. Characters are built with explicit (char) casts to keep this source pure ASCII.
 */
class WatermarkFontSafeTest {

    private static final String UE = String.valueOf((char) 0x00DC); // Ü, encodable in WinAnsi
    private static final String CTRL = String.valueOf((char) 0x009C); // un-encodable control char

    @Test
    void encodableUmlaut_isUnchanged() {
        String text = UE + "BUNGSDOKUMENT";
        assertThat(PdfGenerationService.toFontSafe(PDType1Font.HELVETICA_BOLD, text)).isEqualTo(text);
    }

    @Test
    void unencodableControlChar_isReplacedWithDash_notThrown() {
        String text = CTRL + "BUNGSDOKUMENT";
        assertThat(PdfGenerationService.toFontSafe(PDType1Font.HELVETICA_BOLD, text)).isEqualTo("-BUNGSDOKUMENT");
    }

    @Test
    void mixedText_replacesOnlyUnencodableChars() {
        String text = "A" + CTRL + "B" + CTRL + "C";
        assertThat(PdfGenerationService.toFontSafe(PDType1Font.HELVETICA_BOLD, text)).isEqualTo("A-B-C");
    }

    @Test
    void plainAsciiText_isUnchanged() {
        assertThat(PdfGenerationService.toFontSafe(PDType1Font.HELVETICA_BOLD, "UEBUNGSDOKUMENT"))
            .isEqualTo("UEBUNGSDOKUMENT");
    }
}
