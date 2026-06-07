package at.rolinek.cda.pdf;

import at.rolinek.cda.config.AppProperties;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@Service
public class PdfGenerationService {
    private static final Logger LOG = LoggerFactory.getLogger(PdfGenerationService.class);
    private static final String[] ELGA_REQUIRED_JARS = {
        "CDA2PDF-Demo.jar",
        "CDA2PDF-API.jar",
        "CDA2PDF-DEPS.jar"
    };
    private static final String WRAPPER_CLASS = "CDA2PDFUebung";
    private static final String CLEAN_WRAPPER_CLASS = "CDA2PDFClean";
    private static final String STYLESHEET_FILENAME = "ELGA_Stylesheet_v1.0.xsl";

    private final Path elgaLibDir;
    private final Path elgaWrapperDir;
    private final Path stylesheetPath;
    private final String watermarkText;
    private final float watermarkOpacity;
    private final XmlSafetyGuard xmlSafetyGuard;

    public PdfGenerationService(AppProperties properties, XmlSafetyGuard xmlSafetyGuard) {
        this.elgaLibDir = Path.of(properties.getElgaLibDir()).toAbsolutePath().normalize();
        this.elgaWrapperDir = Path.of(properties.getElgaWrapperDir()).toAbsolutePath().normalize();
        this.stylesheetPath = Path.of(properties.getElgaStylesheetPath()).toAbsolutePath().normalize();
        this.watermarkText = properties.getWatermarkText();
        this.watermarkOpacity = properties.getWatermarkOpacity();
        this.xmlSafetyGuard = xmlSafetyGuard;
    }

    public byte[] generatePdf(String xmlContent) {
        xmlSafetyGuard.requireSafe(xmlContent);
        try {
            byte[] pdf = convertWithElgaWrapper(xmlContent, WRAPPER_CLASS);
            return applyWatermark(pdf);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("PDF-Erstellung fehlgeschlagen", ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF-Erstellung fehlgeschlagen.");
        }
    }

    public byte[] generatePdfClean(String xmlContent) {
        xmlSafetyGuard.requireSafe(xmlContent);
        if (!Files.exists(elgaWrapperDir.resolve(CLEAN_WRAPPER_CLASS + ".class"))) {
            LOG.warn("Sauberer PDF-Konverter nicht bereit (Kompilierung ausstehend): {}", elgaWrapperDir);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "PDF-Konverter ist derzeit nicht verfügbar.");
        }
        try {
            return convertWithElgaWrapper(xmlContent, CLEAN_WRAPPER_CLASS);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("Saubere PDF-Erstellung fehlgeschlagen", ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF-Erstellung fehlgeschlagen.");
        }
    }

    private byte[] convertWithElgaWrapper(String xmlContent, String wrapperClass) throws Exception {
        ensureConverterFiles();

        Path tempDir = Files.createTempDirectory("cda2pdf-");
        try {
            Path inputXml = tempDir.resolve("input.xml");
            Path outputPdf = tempDir.resolve("output.pdf");
            Path localXsl = tempDir.resolve(STYLESHEET_FILENAME);

            Files.copy(stylesheetPath, localXsl);
            Files.writeString(inputXml, ensureStylesheetPi(xmlContent), StandardCharsets.UTF_8);

            String classpath = buildClasspath();
            ProcessBuilder processBuilder = new ProcessBuilder(
                "java",
                "-cp",
                classpath,
                wrapperClass,
                inputXml.toString(),
                tempDir + File.separator,
                outputPdf.getFileName().toString()
            );
            processBuilder.directory(elgaWrapperDir.toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            String output;
            try (var in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            int exit = process.waitFor();
            if (exit != 0 || !Files.exists(outputPdf)) {
                LOG.error("ELGA-Konvertierung fehlgeschlagen (exit={}): {}", exit, output.isBlank() ? "(keine Ausgabe)" : output.trim());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF-Erstellung fehlgeschlagen.");
            }
            return Files.readAllBytes(outputPdf);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private String ensureStylesheetPi(String xmlContent) {
        if (xmlContent.contains("xml-stylesheet")) {
            return xmlContent.replaceAll(
                "<\\?xml-stylesheet\\s+type\\s*=\\s*\"text/xsl\"\\s+href\\s*=\\s*\"[^\"]*\"\\s*\\?>",
                "<?xml-stylesheet type=\"text/xsl\" href=\"" + STYLESHEET_FILENAME + "\"?>"
            );
        }
        String declaration = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
        String pi = "<?xml-stylesheet type=\"text/xsl\" href=\"" + STYLESHEET_FILENAME + "\"?>";
        if (xmlContent.startsWith(declaration)) {
            return xmlContent.replaceFirst(
                "<\\?xml\\s+version=\"1\\.0\"\\s+encoding=\"UTF-8\"\\s*\\?>",
                declaration + "\n" + pi
            );
        }
        return declaration + "\n" + pi + "\n" + xmlContent;
    }

    private String buildClasspath() {
        String separator = File.pathSeparator;
        StringBuilder cp = new StringBuilder(".");
        for (String jar : ELGA_REQUIRED_JARS) {
            cp.append(separator).append(elgaLibDir.resolve(jar));
        }
        return cp.toString();
    }

    private void ensureConverterFiles() {
        if (!Files.isDirectory(elgaLibDir)) {
            LOG.error("ELGA-Library-Verzeichnis fehlt: {}", elgaLibDir);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PDF-Konverter ist derzeit nicht verfügbar.");
        }
        for (String jar : ELGA_REQUIRED_JARS) {
            Path jarPath = elgaLibDir.resolve(jar);
            if (!Files.exists(jarPath)) {
                LOG.error("ELGA-Library fehlt: {}", jarPath);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PDF-Konverter ist derzeit nicht verfügbar.");
            }
        }

        if (!Files.exists(elgaWrapperDir.resolve("CDA2PDFUebung.class"))) {
            LOG.error("Wrapper-Klasse nicht gefunden: {}", elgaWrapperDir.resolve("CDA2PDFUebung.class"));
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PDF-Konverter ist derzeit nicht verfügbar.");
        }
        if (!Files.exists(stylesheetPath)) {
            LOG.error("Stylesheet fehlt: {}", stylesheetPath);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PDF-Konverter ist derzeit nicht verfügbar.");
        }
    }

    private byte[] applyWatermark(byte[] pdfBytes) throws IOException {
        if (watermarkText == null || watermarkText.isBlank()) {
            return pdfBytes;
        }

        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes), MemoryUsageSetting.setupMixed(32 * 1024 * 1024));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (PDPage page : document.getPages()) {
                PDRectangle pageRect = page.getMediaBox();
                float centerX = pageRect.getLowerLeftX() + pageRect.getWidth() / 2f;
                float centerY = pageRect.getLowerLeftY() + pageRect.getHeight() / 2f;
                float fontSize = Math.max(40f, Math.min(pageRect.getWidth(), pageRect.getHeight()) / 8f);
                float textWidth = (PDType1Font.HELVETICA_BOLD.getStringWidth(watermarkText) / 1000f) * fontSize;
                Color color = new Color(150, 150, 150);

                try (PDPageContentStream contentStream =
                         new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
                    graphicsState.setNonStrokingAlphaConstant(watermarkOpacity);
                    graphicsState.setStrokingAlphaConstant(watermarkOpacity);
                    contentStream.setGraphicsStateParameters(graphicsState);
                    contentStream.setNonStrokingColor(color);
                    contentStream.beginText();
                    contentStream.setFont(PDType1Font.HELVETICA_BOLD, fontSize);
                    contentStream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45), centerX, centerY));
                    contentStream.newLineAtOffset(-textWidth / 2f, 0f);
                    contentStream.showText(watermarkText);
                    contentStream.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // temp clean-up best-effort
                }
            });
        } catch (IOException ignored) {
            // temp clean-up best-effort
        }
    }
}
