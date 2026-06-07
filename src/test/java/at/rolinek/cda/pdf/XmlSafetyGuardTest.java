package at.rolinek.cda.pdf;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlSafetyGuardTest {

    private final XmlSafetyGuard guard = new XmlSafetyGuard();

    // (a) Plain well-formed XML must pass without any exception
    @Test
    void wellFormedXml_passes() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<ClinicalDocument xmlns=\"urn:hl7-org:v3\">\n"
                + "  <title>Test</title>\n"
                + "</ClinicalDocument>";
        assertThatCode(() -> guard.requireSafe(xml)).doesNotThrowAnyException();
    }

    // (b) DOCTYPE declaration must be rejected with HTTP 400
    @Test
    void xmlWithDoctype_isRejected() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE foo [<!ELEMENT foo ANY>]>\n"
                + "<foo>bar</foo>";
        assertThatThrownBy(() -> guard.requireSafe(xml))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Ungültiges oder nicht erlaubtes XML.");
                });
    }

    // (b) Classic XXE payload with external entity must also be rejected
    @Test
    void xmlWithXxePayload_isRejected() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE foo [\n"
                + "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">\n"
                + "]>\n"
                + "<foo>&xxe;</foo>";
        assertThatThrownBy(() -> guard.requireSafe(xml))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    // (c) Malformed XML must be rejected with HTTP 400
    @Test
    void malformedXml_isRejected() {
        String xml = "<unclosed><tag>missing end tag";
        assertThatThrownBy(() -> guard.requireSafe(xml))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).isEqualTo("Ungültiges oder nicht erlaubtes XML.");
                });
    }

    // Error message must NOT expose internal parser details
    @Test
    void rejectedXml_doesNotLeakParserInternals() {
        String xml = "<!DOCTYPE leak [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><foo/>";
        assertThatThrownBy(() -> guard.requireSafe(xml))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    String reason = rse.getReason();
                    // Must not contain filesystem paths or parser exception class names
                    assertThat(reason).doesNotContain("/etc");
                    assertThat(reason).doesNotContain("file:");
                    assertThat(reason).doesNotContain("Exception");
                    assertThat(reason).doesNotContain("SAX");
                });
    }
}
