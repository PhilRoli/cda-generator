package at.rolinek.cda.pdf;

import at.rolinek.cda.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bounds (size cap + concurrency gate) for {@link PdfGenerationService}. These tests
 * deliberately exercise the guards that run BEFORE the heavy ELGA conversion, so they need
 * neither the ELGA jars nor a sample document.
 */
class PdfGenerationBoundsTest {

    private PdfGenerationService service(AppProperties props) {
        return new PdfGenerationService(props, new XmlSafetyGuard());
    }

    private AppProperties props() {
        return new AppProperties();
    }

    // --- Size cap: /api/pdf JSON path ---

    @Test
    void requireWithinSizeLimit_underLimit_passes() {
        AppProperties props = props();
        props.getPdf().setMaxXmlBytes(1024);
        service(props).requireWithinSizeLimit("a".repeat(1024));
    }

    @Test
    void requireWithinSizeLimit_overLimit_throws413() {
        AppProperties props = props();
        props.getPdf().setMaxXmlBytes(1024);

        assertThatThrownBy(() -> service(props).requireWithinSizeLimit("a".repeat(1025)))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                assertThat(ex.getReason()).isEqualTo("XML-Inhalt ist zu groß.");
            });
    }

    @Test
    void requireWithinSizeLimit_countsUtf8Bytes_notCharacters() {
        AppProperties props = props();
        props.getPdf().setMaxXmlBytes(3);
        // "äöü" is 3 chars but 6 UTF-8 bytes — must be rejected.
        assertThatThrownBy(() -> service(props).requireWithinSizeLimit("äöü"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void generatePdf_oversizedXml_throws413BeforeConversion() {
        AppProperties props = props();
        props.getPdf().setMaxXmlBytes(8);

        assertThatThrownBy(() -> service(props).generatePdf("<xml>far too large</xml>"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    // --- Concurrency gate: fast-fail with 503 when saturated ---

    @Test
    void generatePdfClean_gateSaturated_fastFailsWith503() throws Exception {
        AppProperties props = props();
        props.getPdf().setMaxConcurrent(1);
        props.getPdf().setAcquireTimeoutSeconds(1); // short, deterministic fast-fail window
        props.getPdf().setMaxXmlBytes(10_000_000);

        PdfGenerationService svc = service(props);

        // Simulate a busy server by draining the single gate permit. The call must then
        // fail to acquire within acquireTimeoutSeconds and return 503 — without ever
        // reaching the (jar-dependent) converter.
        Semaphore gate = readGate(svc);
        gate.acquire();
        try {
            // The exact 503 reason only originates from the gate-saturation path, so the
            // status + reason assertions prove the call fast-failed at the gate without
            // ever reaching the (jar-dependent) converter. No wall-clock assertion is used
            // here — timing lower-bounds are flaky under CI scheduling.
            assertThatThrownBy(() -> svc.generatePdfClean("<x/>"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getReason()).isEqualTo("Server ist ausgelastet. Bitte später erneut versuchen.");
                });
        } finally {
            gate.release();
        }
    }

    private Semaphore readGate(PdfGenerationService svc) throws Exception {
        Field f = PdfGenerationService.class.getDeclaredField("conversionGate");
        f.setAccessible(true);
        return (Semaphore) f.get(svc);
    }
}
