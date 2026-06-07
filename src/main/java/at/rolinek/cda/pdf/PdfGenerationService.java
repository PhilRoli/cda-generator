package at.rolinek.cda.pdf;

import at.rolinek.cda.config.AppProperties;
import jakarta.annotation.PreDestroy;
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
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Converts CDA XML to PDF by calling the ELGA CDA2PDF library in-process.
 *
 * <p>The ELGA jars are not on the application's compile classpath (they are mounted at
 * runtime), so the converter classes are loaded lazily through a dedicated
 * {@link URLClassLoader} over the jars in {@code elgaLibDir} and invoked via reflection.
 *
 * <p>The converter resolves its XSL stylesheet from resources bundled inside the jars
 * (the {@code templates/} and {@code RootTemplateDefault.xsl} tree in CDA2PDF-API.jar);
 * the {@code <?xml-stylesheet?>} processing instruction in the input XML is ignored, so
 * no stylesheet file copying or PI rewriting is required.
 *
 * <p>The ELGA converter is NOT thread-safe (concurrent conversions corrupt shared static
 * XSLT-compiler state and return null), so all conversions must run strictly serialised.
 *
 * <p>The endpoints are unauthenticated and each conversion is heavy, so access is bounded
 * by two cooperating mechanisms:
 * <ul>
 *   <li>a fair {@link Semaphore} (default 1 permit) admits callers to the conversion gate;
 *       a flood of requests fast-fails with 503 after {@code acquireTimeoutSeconds} instead
 *       of piling up threads;</li>
 *   <li>a single-thread {@link ExecutorService} actually runs the conversion. The single
 *       thread reinforces serialisation, and a {@code future.get(timeout)} frees the request
 *       thread if a conversion hangs (503) without ever starting a second conversion on a
 *       new thread.</li>
 * </ul>
 */
@Service
public class PdfGenerationService {
    private static final Logger LOG = LoggerFactory.getLogger(PdfGenerationService.class);

    private static final String[] ELGA_REQUIRED_JARS = {
        "CDA2PDF-Demo.jar",
        "CDA2PDF-API.jar",
        "CDA2PDF-DEPS.jar"
    };
    private static final String BUILDER_CLASS = "at.gv.elga.cda2pdflib.addon.CDA2PDFBuilder";
    private static final String CONVERTER_CLASS = "at.gv.elga.cda2pdflib.CDA2PDFConverter";

    private static final String UEBUNG_AUTH_USER = "Übungs-Generator";
    private static final String UEBUNG_BANNER_TEXT = "ÜBUNGSDOKUMENT — NUR FÜR TRAININGS!";
    private static final String CLEAN_AUTH_USER = "CDA-Konverter";

    private final Path elgaLibDir;
    private final String watermarkText;
    private final float watermarkOpacity;
    private final XmlSafetyGuard xmlSafetyGuard;

    private final long maxXmlBytes;
    private final long acquireTimeoutSeconds;
    private final long conversionTimeoutSeconds;

    /**
     * Bounds how many callers may be admitted to the conversion gate. With a single permit
     * (default) this both serialises conversions and fast-fails a flood of waiters.
     * Fair so waiters are served in arrival order.
     */
    private final Semaphore conversionGate;

    /**
     * Runs the actual conversion on a single dedicated thread. The single thread enforces
     * serialisation at the executor level too: even if a hung conversion is left running
     * (it may ignore interruption), the next admitted task simply queues behind it on the
     * same thread — two conversions can never overlap.
     */
    private final ExecutorService conversionExecutor =
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "pdf-conversion");
            t.setDaemon(true);
            return t;
        });

    /** Lazily initialised, cached reflection handles for the ELGA converter. */
    private volatile ElgaConverter elgaConverter;

    public PdfGenerationService(AppProperties properties, XmlSafetyGuard xmlSafetyGuard) {
        this.elgaLibDir = Path.of(properties.getElgaLibDir()).toAbsolutePath().normalize();
        this.watermarkText = properties.getWatermarkText();
        this.watermarkOpacity = properties.getWatermarkOpacity();
        this.xmlSafetyGuard = xmlSafetyGuard;

        AppProperties.Pdf pdf = properties.getPdf();
        this.maxXmlBytes = pdf.getMaxXmlBytes();
        this.acquireTimeoutSeconds = pdf.getAcquireTimeoutSeconds();
        this.conversionTimeoutSeconds = pdf.getConversionTimeoutSeconds();
        this.conversionGate = new Semaphore(Math.max(1, pdf.getMaxConcurrent()), true);
    }

    @PreDestroy
    void shutdown() {
        conversionExecutor.shutdownNow();
    }

    public byte[] generatePdf(String xmlContent) {
        requireWithinSizeLimit(xmlContent);
        xmlSafetyGuard.requireSafe(xmlContent);
        try {
            byte[] pdf = convert(xmlContent, Variant.UEBUNG);
            return applyWatermark(pdf);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("PDF-Erstellung fehlgeschlagen", ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF-Erstellung fehlgeschlagen.");
        }
    }

    public byte[] generatePdfClean(String xmlContent) {
        requireWithinSizeLimit(xmlContent);
        xmlSafetyGuard.requireSafe(xmlContent);
        try {
            return convert(xmlContent, Variant.CLEAN);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("Saubere PDF-Erstellung fehlgeschlagen", ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF-Erstellung fehlgeschlagen.");
        }
    }

    private enum Variant { UEBUNG, CLEAN }

    /**
     * Rejects oversized XML before any heavy work. Spring's default JSON body size is large,
     * so this explicit guard is the cap for the {@code /api/pdf} JSON path. Package-private
     * and side-effect-free so it can be unit-tested without the ELGA jars.
     */
    void requireWithinSizeLimit(String xmlContent) {
        if (xmlContent == null) {
            return;
        }
        long bytes = xmlContent.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > maxXmlBytes) {
            LOG.warn("XML-Inhalt abgelehnt: {} Bytes überschreiten Limit {} Bytes", bytes, maxXmlBytes);
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "XML-Inhalt ist zu groß.");
        }
    }

    private byte[] convert(String xmlContent, Variant variant) throws Exception {
        byte[] xmlBytes = xmlContent.getBytes(StandardCharsets.UTF_8);

        // Bounded, fast-failing admission FIRST: a flood of unauthenticated requests fails
        // with 503 after acquireTimeoutSeconds rather than piling up waiting threads. Done
        // before resolving the converter so no work happens for rejected callers.
        boolean acquired;
        try {
            acquired = conversionGate.tryAcquire(acquireTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Server ist ausgelastet. Bitte später erneut versuchen.");
        }
        if (!acquired) {
            LOG.warn("PDF-Gate ausgelastet: kein Permit innerhalb von {}s erhalten", acquireTimeoutSeconds);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Server ist ausgelastet. Bitte später erneut versuchen.");
        }

        try {
            ElgaConverter converter = getElgaConverter();
            Future<byte[]> future =
                conversionExecutor.submit(() -> converter.convert(xmlBytes, variant));
            byte[] pdf = awaitConversion(future, variant);
            if (pdf == null || pdf.length == 0) {
                LOG.error("ELGA-Konvertierung lieferte kein PDF (Variante {})", variant);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF-Erstellung fehlgeschlagen.");
            }
            return pdf;
        } finally {
            // Release in finally so a timed-out request (or a converter-init failure) never
            // leaks a permit. Releasing the permit while a stuck task still occupies the single
            // executor thread is safe: the executor itself serialises, so a newly admitted
            // caller's task just queues behind the stuck task on that one thread.
            conversionGate.release();
        }
    }

    private byte[] awaitConversion(Future<byte[]> future, Variant variant) throws Exception {
        try {
            return future.get(conversionTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            // Free the request thread. We cancel(true) to request interruption, but the ELGA
            // converter may ignore it and keep running on the single executor thread. That is
            // acceptable: the single-thread executor serialises, so the next admitted task
            // simply queues behind the stuck one — two conversions can never overlap.
            future.cancel(true);
            LOG.error("Zeitüberschreitung bei der PDF-Erstellung (Variante {}, Limit {}s)",
                variant, conversionTimeoutSeconds, ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Zeitüberschreitung bei der PDF-Erstellung.");
        } catch (ExecutionException ex) {
            // Unwrap so the underlying failure is logged server-side; the caller only ever
            // sees the generic German message thrown by the calling generate* method.
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof ResponseStatusException rse) {
                throw rse;
            }
            if (cause instanceof Exception e) {
                throw e;
            }
            throw ex;
        }
    }

    /** Builds and caches the reflection handles on first use (double-checked locking). */
    private ElgaConverter getElgaConverter() {
        ElgaConverter local = elgaConverter;
        if (local == null) {
            synchronized (this) {
                local = elgaConverter;
                if (local == null) {
                    local = createElgaConverter();
                    elgaConverter = local;
                }
            }
        }
        return local;
    }

    private ElgaConverter createElgaConverter() {
        if (!Files.isDirectory(elgaLibDir)) {
            LOG.error("ELGA-Library-Verzeichnis fehlt: {}", elgaLibDir);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "PDF-Konverter ist derzeit nicht verfügbar.");
        }
        try {
            URL[] urls = new URL[ELGA_REQUIRED_JARS.length];
            for (int i = 0; i < ELGA_REQUIRED_JARS.length; i++) {
                Path jarPath = elgaLibDir.resolve(ELGA_REQUIRED_JARS[i]);
                if (!Files.exists(jarPath)) {
                    LOG.error("ELGA-Library fehlt: {}", jarPath);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "PDF-Konverter ist derzeit nicht verfügbar.");
                }
                urls[i] = jarPath.toUri().toURL();
            }
            // Parent = platform class loader to isolate the ELGA jars' bundled dependencies
            // (FOP, Xalan, Log4j, ...) from the application classpath.
            URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
            Class<?> builderClass = Class.forName(BUILDER_CLASS, true, loader);
            Class<?> converterClass = Class.forName(CONVERTER_CLASS, true, loader);
            return new ElgaConverter(loader, builderClass, converterClass);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("ELGA-Konverter konnte nicht initialisiert werden", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "PDF-Konverter ist derzeit nicht verfügbar.");
        }
    }

    /** Holds cached reflection metadata for the ELGA converter classes. */
    private static final class ElgaConverter {
        private final ClassLoader loader;
        private final java.lang.reflect.Constructor<?> builderCtor;
        private final java.lang.reflect.Constructor<?> converterCtor;
        private final Method setAuthUser;
        private final Method hideDocumentInformation;
        private final Method setBannerText;
        private final Method enableFullDocument;
        private final Method xmlToPdfPerXsl;

        ElgaConverter(ClassLoader loader, Class<?> builderClass, Class<?> converterClass) throws Exception {
            this.loader = loader;
            this.builderCtor = builderClass.getDeclaredConstructor();
            this.converterCtor = converterClass.getConstructor(builderClass);
            this.setAuthUser = builderClass.getMethod("setAuthUser", String.class);
            this.hideDocumentInformation = builderClass.getMethod("hideDocumentInformation");
            this.setBannerText = builderClass.getMethod("setBannerText", String.class);
            this.enableFullDocument = builderClass.getMethod("enableFullDocument");
            this.xmlToPdfPerXsl = converterClass.getMethod("xmlToPdfPerXsl", InputStream.class);
        }

        /** Performs a single conversion. Only ever invoked on the single conversion thread. */
        byte[] convert(byte[] xmlBytes, Variant variant) throws Exception {
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
            try {
                Object builder = builderCtor.newInstance();
                if (variant == Variant.UEBUNG) {
                    setAuthUser.invoke(builder, UEBUNG_AUTH_USER);
                    // enableFullDocument() bewusst NICHT aufrufen → "normale" (kürzere) Variante
                    hideDocumentInformation.invoke(builder);
                    setBannerText.invoke(builder, UEBUNG_BANNER_TEXT);
                } else {
                    setAuthUser.invoke(builder, CLEAN_AUTH_USER);
                    enableFullDocument.invoke(builder);
                }
                Object converter = converterCtor.newInstance(builder);
                Object result = xmlToPdfPerXsl.invoke(converter, new ByteArrayInputStream(xmlBytes));
                if (result == null) {
                    return null;
                }
                return ((ByteArrayOutputStream) result).toByteArray();
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
            }
        }
    }

    private byte[] applyWatermark(byte[] pdfBytes) throws IOException {
        if (watermarkText == null || watermarkText.isBlank()) {
            return pdfBytes;
        }

        // Defense-in-depth: a watermark character the standard font cannot encode
        // (e.g. a misconfigured/mojibake'd text) must never crash PDF generation.
        // Replace any un-encodable character with '-' so a watermark is always drawn.
        String safeText = toFontSafe(watermarkText);
        if (safeText.isBlank()) {
            LOG.warn("Wasserzeichen-Text enthält keine darstellbaren Zeichen; Wasserzeichen wird übersprungen.");
            return pdfBytes;
        }

        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes), MemoryUsageSetting.setupMixed(32 * 1024 * 1024));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (PDPage page : document.getPages()) {
                PDRectangle pageRect = page.getMediaBox();
                float centerX = pageRect.getLowerLeftX() + pageRect.getWidth() / 2f;
                float centerY = pageRect.getLowerLeftY() + pageRect.getHeight() / 2f;
                float fontSize = Math.max(40f, Math.min(pageRect.getWidth(), pageRect.getHeight()) / 8f);
                float textWidth = (PDType1Font.HELVETICA_BOLD.getStringWidth(safeText) / 1000f) * fontSize;
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
                    contentStream.showText(safeText);
                    contentStream.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Returns a copy of {@code text} in which every character the given font cannot
     * encode is replaced by '-', so width measurement and rendering never throw.
     */
    static String toFontSafe(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            try {
                PDType1Font.HELVETICA_BOLD.getStringWidth(ch);
                sb.append(ch);
            } catch (Exception ex) {
                sb.append('-');
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }
}
