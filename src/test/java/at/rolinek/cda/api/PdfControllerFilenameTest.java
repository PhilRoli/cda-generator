package at.rolinek.cda.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests for {@link PdfController#toPdfFilename(String)}.
 * No Spring context required — the method is package-private static.
 */
class PdfControllerFilenameTest {

    // -------------------------------------------------------------------------
    // null / blank → fallback "entlassungsbrief.pdf"
    // -------------------------------------------------------------------------

    @Test
    void null_returnsFallback() {
        assertThat(PdfController.toPdfFilename(null)).isEqualTo("entlassungsbrief.pdf");
    }

    @Test
    void emptyString_returnsFallback() {
        assertThat(PdfController.toPdfFilename("")).isEqualTo("entlassungsbrief.pdf");
    }

    @Test
    void blankString_returnsFallback() {
        assertThat(PdfController.toPdfFilename("   ")).isEqualTo("entlassungsbrief.pdf");
    }

    // -------------------------------------------------------------------------
    // .xml suffix is stripped and .pdf appended
    // -------------------------------------------------------------------------

    @Test
    void xmlExtension_isStrippedAndPdfAppended() {
        assertThat(PdfController.toPdfFilename("report.xml")).isEqualTo("report.pdf");
    }

    // -------------------------------------------------------------------------
    // .pdf suffix: existing .pdf is stripped before re-appending (no double .pdf)
    // -------------------------------------------------------------------------

    @Test
    void pdfExtension_noDoublePdf() {
        assertThat(PdfController.toPdfFilename("document.pdf")).isEqualTo("document.pdf");
    }

    // -------------------------------------------------------------------------
    // Characters outside [a-zA-Z0-9._-] are replaced with '-'
    // -------------------------------------------------------------------------

    @Test
    void specialChars_areReplacedWithDash() {
        // 'a/b c:d.xml' → strip .xml → 'a/b c:d' → sanitize → 'a-b-c-d' → append .pdf
        assertThat(PdfController.toPdfFilename("a/b c:d.xml")).isEqualTo("a-b-c-d.pdf");
    }

    @Test
    void unicodeChars_areReplacedWithDash() {
        // 'ü' is a single char outside the safe set → replaced with one '-'
        // "bericht-ü.xml" → strip .xml → "bericht-ü" → sanitize → "bericht--" → append .pdf
        assertThat(PdfController.toPdfFilename("bericht-ü.xml")).isEqualTo("bericht--.pdf");
    }

    // -------------------------------------------------------------------------
    // Name that sanitizes to blank (all forbidden chars) → fallback
    // -------------------------------------------------------------------------

    @Test
    void nameThatSanitizesToBlank_returnsFallback() {
        // ":::" → strip any .xml/.pdf (none here) → sanitize → "---" → not blank → "---.pdf"
        // But a name consisting ONLY of whitespace-equivalent-after-sanitize chars:
        // Java's String.isBlank() checks Unicode whitespace; '-' is not whitespace, so "---"
        // would NOT fall back.  Instead we need a name whose sanitized form IS blank.
        // The regex replaces [^a-zA-Z0-9._-] with '-', so the result is never empty unless
        // the original (after extension removal) was empty — i.e. the file was named ".xml".
        assertThat(PdfController.toPdfFilename(".xml")).isEqualTo("entlassungsbrief.pdf");
    }

    // -------------------------------------------------------------------------
    // Name with dots and dashes already in it — preserved
    // -------------------------------------------------------------------------

    @Test
    void dotsAndDashesInName_arePreserved() {
        assertThat(PdfController.toPdfFilename("my-report_2024.pdf")).isEqualTo("my-report_2024.pdf");
    }

    @Test
    void nameWithInternalDot_isPreserved() {
        assertThat(PdfController.toPdfFilename("v1.2.3.xml")).isEqualTo("v1.2.3.pdf");
    }
}
