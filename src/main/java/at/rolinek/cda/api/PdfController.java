package at.rolinek.cda.api;

import at.rolinek.cda.config.AppProperties;
import at.rolinek.cda.pdf.PdfGenerationService;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api")
@Validated
public class PdfController {

    private static final Logger LOG = LoggerFactory.getLogger(PdfController.class);

    private final PdfGenerationService pdfGenerationService;
    private final AppProperties appProperties;

    public PdfController(PdfGenerationService pdfGenerationService, AppProperties appProperties) {
        this.pdfGenerationService = pdfGenerationService;
        this.appProperties = appProperties;
    }

    @PostMapping(value = "/pdf", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generatePdf(@RequestBody PdfRequest request) {
        if (request == null || request.xml() == null || request.xml().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "XML-Inhalt fehlt.");
        }

        byte[] pdf = pdfGenerationService.generatePdf(request.xml());
        String filename = toPdfFilename(request.fileName());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentLength(pdf.length);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @PostMapping(value = "/pdf/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> uploadXml(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(name = "X-Clean-Pdf-Password", required = false) String password) throws IOException {

        verifyCleanPdfPassword(password);

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "XML-Datei fehlt.");
        }
        String xmlContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        String filename = toPdfFilename(file.getOriginalFilename());
        byte[] pdf = pdfGenerationService.generatePdfClean(xmlContent);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentLength(pdf.length);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    /**
     * Verifies the supplied password against the configured clean-PDF password.
     * Fail-closed: if no password is configured, ALL requests are rejected (403).
     * Uses constant-time comparison to prevent timing attacks.
     *
     * @param provided the password from the request header (may be null)
     * @throws ResponseStatusException 403 if the endpoint is not configured or the password is wrong
     */
    void verifyCleanPdfPassword(String provided) {
        String configured = appProperties.getCleanPdfPassword();
        if (configured == null || configured.isBlank()) {
            LOG.warn("Anfrage an /api/pdf/upload abgelehnt: APP_CLEAN_PDF_PASSWORD ist nicht konfiguriert.");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Funktion ist nicht konfiguriert.");
        }
        if (!constantTimeEquals(provided, configured)) {
            LOG.warn("Anfrage an /api/pdf/upload abgelehnt: ungültiges Passwort.");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ungültiges Passwort.");
        }
    }

    /**
     * Constant-time string comparison that does not short-circuit on length difference,
     * preventing timing-based password oracle attacks.
     * Both inputs are SHA-256 hashed before comparing via MessageDigest.isEqual so that
     * the fixed-length digests prevent the length check inside isEqual from leaking
     * information about the actual password length.
     */
    static boolean constantTimeEquals(String a, String b) {
        // Treat null as empty string so the comparison always takes the same path
        byte[] aHash = sha256((a != null ? a : "").getBytes(StandardCharsets.UTF_8));
        byte[] bHash = sha256((b != null ? b : "").getBytes(StandardCharsets.UTF_8));
        return MessageDigest.isEqual(aHash, bHash);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java spec — this can never happen
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }

    // Package-private (and static) for unit testing in PdfControllerFilenameTest.
    static String toPdfFilename(String requested) {
        String base = (requested == null || requested.isBlank()) ? "entlassungsbrief" : requested;
        base = base.replaceAll("\\.xml$", "").replaceAll("\\.pdf$", "");
        base = base.replaceAll("[^a-zA-Z0-9._-]", "-");
        if (base.isBlank()) {
            base = "entlassungsbrief";
        }
        return base + ".pdf";
    }

    public record PdfRequest(@NotBlank String xml, String fileName) {}
}
