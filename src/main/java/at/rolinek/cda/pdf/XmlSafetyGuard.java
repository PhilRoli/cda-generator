package at.rolinek.cda.pdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Guards against unsafe XML documents before passing them to the ELGA converter.
 * Rejects documents that declare a DOCTYPE (XXE/SSRF defense) and malformed XML.
 */
@Component
public class XmlSafetyGuard {

    private static final Logger LOG = LoggerFactory.getLogger(XmlSafetyGuard.class);

    /**
     * Validates that the given XML string is well-formed and contains no DOCTYPE declaration.
     *
     * @param xml the XML content to validate
     * @throws ResponseStatusException HTTP 400 if the XML is malformed or contains a DOCTYPE
     */
    public void requireSafe(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Reject DOCTYPE declarations — this causes parsing to throw if a DOCTYPE is present
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            // Disable external general entities
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            // Disable external parameter entities
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            factory.setNamespaceAware(true);

            factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            LOG.warn("XML-Sicherheitsüberprüfung fehlgeschlagen: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiges oder nicht erlaubtes XML.");
        }
    }
}
