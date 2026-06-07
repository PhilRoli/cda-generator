package at.rolinek.cda.pdf;

import at.rolinek.cda.config.AppProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises the real in-process ELGA conversion. Skipped (via assumption) when the
 * ELGA jars are not present under {@code elga-lib/} so CI without the jars still passes.
 */
class PdfGenerationInProcessTest {

    private static final Path ELGA_LIB_DIR = Path.of("elga-lib");
    private static final Path SAMPLE_XML = Path.of("assets/beispiel-entlassungsbrief.xml");

    private PdfGenerationService newService() {
        AppProperties props = new AppProperties();
        // defaults already point elgaLibDir -> "elga-lib"
        return new PdfGenerationService(props, new XmlSafetyGuard());
    }

    private String sampleXml() throws Exception {
        return Files.readString(SAMPLE_XML, StandardCharsets.UTF_8);
    }

    private boolean elgaAvailable() {
        return Files.exists(ELGA_LIB_DIR.resolve("CDA2PDF-API.jar"))
            && Files.exists(ELGA_LIB_DIR.resolve("CDA2PDF-DEPS.jar"))
            && Files.exists(ELGA_LIB_DIR.resolve("CDA2PDF-Demo.jar"))
            && Files.exists(SAMPLE_XML);
    }

    @Test
    void generatePdf_uebung_producesWatermarkedPdf() throws Exception {
        assumeTrue(elgaAvailable(), "ELGA jars / sample XML not present — skipping in-process conversion test");
        byte[] pdf = newService().generatePdf(sampleXml());
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(10_000);
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");

        // The default watermark text must have been overlaid by applyWatermark().
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains(new AppProperties().getWatermarkText());
        }
    }

    @Test
    void generatePdfClean_producesPdf() throws Exception {
        assumeTrue(elgaAvailable(), "ELGA jars / sample XML not present — skipping in-process conversion test");
        byte[] pdf = newService().generatePdfClean(sampleXml());
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(10_000);
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }
}
