package at.rolinek.cda.pdf;

import at.rolinek.cda.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A conversion that hangs past the timeout (the ELGA library may ignore interruption)
 * must not wedge the service: the next request has to get a fresh converter instead of
 * queueing behind the stuck one on the single conversion thread.
 */
class PdfGenerationTimeoutTest {

    private static final byte[] FAKE_PDF = "%PDF-fake".getBytes(StandardCharsets.ISO_8859_1);

    /** Released in teardown so the abandoned "stuck" thread can finish. */
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void releaseStuckThread() {
        release.countDown();
    }

    /** Blocks until released and, like the ELGA converter may, ignores interruption. */
    private PdfGenerationService.Converter hangingConverter() {
        return (xml, variant) -> {
            while (release.getCount() > 0) {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    // swallow, as a misbehaving library would
                }
            }
            return FAKE_PDF;
        };
    }

    @Test
    void timedOutConversion_doesNotBlockTheNextRequest() {
        AppProperties props = new AppProperties();
        props.getPdf().setConversionTimeoutSeconds(1);
        props.getPdf().setAcquireTimeoutSeconds(1);

        AtomicInteger created = new AtomicInteger();
        PdfGenerationService svc = new PdfGenerationService(props, new XmlSafetyGuard(),
            () -> created.incrementAndGet() == 1 ? hangingConverter() : (xml, variant) -> FAKE_PDF);

        assertThatThrownBy(() -> svc.generatePdfClean("<x/>"))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(ex.getReason()).isEqualTo("Zeitüberschreitung bei der PDF-Erstellung.");
            });

        assertThat(svc.generatePdfClean("<x/>")).isEqualTo(FAKE_PDF);
    }
}
