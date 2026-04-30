package at.rolinek.cda.api;

import at.rolinek.cda.pdf.PdfGenerationService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
@Validated
public class PdfController {
    private final PdfGenerationService pdfGenerationService;

    public PdfController(PdfGenerationService pdfGenerationService) {
        this.pdfGenerationService = pdfGenerationService;
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
    public ResponseEntity<byte[]> uploadXml(@RequestParam("file") MultipartFile file) throws IOException {
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

    private String toPdfFilename(String requested) {
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
