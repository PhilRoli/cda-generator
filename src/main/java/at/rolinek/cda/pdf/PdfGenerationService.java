package at.rolinek.cda.pdf;

import at.rolinek.cda.config.AppProperties;
import jakarta.annotation.PreDestroy;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.logging.slf4j.SLF4JProvider;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

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

    /**
     * Bundled watermark font (DejaVu Sans Bold, Bitstream Vera license — see
     * {@code fonts/LICENSE_DEJAVU.txt}). Embedded explicitly rather than using PDFBox's
     * standard-14 fonts because this container has none of the matching system fonts
     * (Helvetica/Times/Courier), so PDFBox would substitute at render time and log a
     * WARN per base-14 font name every time. It also gives {@link #toFontSafe} full
     * Unicode coverage instead of WinAnsiEncoding's limited Latin-1 range.
     */
    private static final String WATERMARK_FONT_RESOURCE = "/fonts/DejaVuSans-Bold.ttf";
    private static final byte[] WATERMARK_FONT_BYTES = loadWatermarkFontBytes();
    private static final CmapLookup WATERMARK_FONT_CMAP = loadWatermarkFontCmap(WATERMARK_FONT_BYTES);

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
     * Runs the actual conversion on a single dedicated thread, which serialises conversions
     * at the executor level too. A conversion that hangs past the timeout (the ELGA library
     * may ignore interruption) is abandoned together with its executor and converter — see
     * {@link #abandonStuckConversion} — so later requests don't queue behind it forever.
     */
    private volatile ExecutorService conversionExecutor = newConversionExecutor();

    /** Creates the converter on first use; the ELGA reflection loader in production. */
    private final Supplier<Converter> converterFactory;

    /** Lazily initialised, cached converter (ELGA reflection handles in production). */
    private volatile Converter elgaConverter;

    @Autowired
    public PdfGenerationService(AppProperties properties, XmlSafetyGuard xmlSafetyGuard) {
        this(properties, xmlSafetyGuard, null);
    }

    /** Package-private so tests can substitute the jar-dependent ELGA converter. */
    PdfGenerationService(AppProperties properties, XmlSafetyGuard xmlSafetyGuard,
                         Supplier<Converter> converterFactory) {
        this.converterFactory = converterFactory != null ? converterFactory : this::createElgaConverter;
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
        try {
            return convert(xmlContent, Variant.CLEAN);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.error("Saubere PDF-Erstellung fehlgeschlagen", ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF-Erstellung fehlgeschlagen.");
        }
    }

    enum Variant { UEBUNG, CLEAN }

    /** A single CDA-to-PDF conversion. Only ever invoked on the single conversion thread. */
    interface Converter {
        byte[] convert(byte[] xmlBytes, Variant variant) throws Exception;
    }

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
            // The safety check builds a full DOM, so it runs inside the gate: parallel
            // requests must not each hold a multi-MB document in heap.
            xmlSafetyGuard.requireSafe(xmlContent);
            Converter converter = getElgaConverter();
            ExecutorService executor = conversionExecutor;
            Future<byte[]> future = executor.submit(() -> converter.convert(xmlBytes, variant));
            byte[] pdf = awaitConversion(future, executor, variant);
            if (pdf == null || pdf.length == 0) {
                LOG.error("ELGA-Konvertierung lieferte kein PDF (Variante {})", variant);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF-Erstellung fehlgeschlagen.");
            }
            return pdf;
        } finally {
            // Release in finally so a timed-out request (or a converter-init failure) never
            // leaks a permit. A stuck task never shares a thread or converter with the next
            // admitted caller: awaitConversion abandons both on timeout.
            conversionGate.release();
        }
    }

    private byte[] awaitConversion(Future<byte[]> future, ExecutorService executor, Variant variant)
            throws Exception {
        try {
            return future.get(conversionTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            // Free the request thread. cancel(true) requests interruption, but the ELGA
            // converter may ignore it and keep running, so abandon its thread and converter.
            future.cancel(true);
            abandonStuckConversion(executor);
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

    /**
     * Replaces the executor (and thereby its possibly still-running thread) and drops the
     * cached converter, so the next conversion runs on a fresh thread with a fresh ELGA
     * class loader that shares no static state with the stuck one. The abandoned daemon
     * thread is left to finish (or not) on its own; Java offers no way to kill it.
     */
    private synchronized void abandonStuckConversion(ExecutorService stuck) {
        if (conversionExecutor != stuck) {
            return; // already replaced by a concurrent timeout
        }
        stuck.shutdownNow();
        conversionExecutor = newConversionExecutor();
        elgaConverter = null;
        LOG.warn("Hängende PDF-Konvertierung aufgegeben; neuer Konverter-Thread wird verwendet.");
    }

    private static ExecutorService newConversionExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "pdf-conversion");
            t.setDaemon(true);
            return t;
        });
    }

    /** Builds and caches the reflection handles on first use (double-checked locking). */
    private Converter getElgaConverter() {
        Converter local = elgaConverter;
        if (local == null) {
            synchronized (this) {
                local = elgaConverter;
                if (local == null) {
                    local = converterFactory.get();
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
            URL[] elgaJarUrls = new URL[ELGA_REQUIRED_JARS.length];
            for (int i = 0; i < ELGA_REQUIRED_JARS.length; i++) {
                Path jarPath = elgaLibDir.resolve(ELGA_REQUIRED_JARS[i]);
                if (!Files.exists(jarPath)) {
                    LOG.error("ELGA-Library fehlt: {}", jarPath);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "PDF-Konverter ist derzeit nicht verfügbar.");
                }
                elgaJarUrls[i] = jarPath.toUri().toURL();
            }
            // Parent = platform class loader to isolate the ELGA jars' bundled dependencies
            // (FOP, Xalan, Log4j, ...) from the application classpath. The log4j-to-slf4j
            // bridge jar (plus its own slf4j-api dependency, since the platform class loader
            // parent can't see the app's classpath either) is added on top so the ELGA jars'
            // bundled log4j-api finds a provider and routes through the app's Logback config,
            // instead of finding none and logging "Log4j API could not find a logging
            // provider" straight to stderr.
            URL[] urls = Arrays.copyOf(elgaJarUrls, elgaJarUrls.length + 2);
            urls[elgaJarUrls.length] = SLF4JProvider.class.getProtectionDomain().getCodeSource().getLocation();
            urls[elgaJarUrls.length + 1] = Logger.class.getProtectionDomain().getCodeSource().getLocation();
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

    private static byte[] loadWatermarkFontBytes() {
        try (InputStream in = PdfGenerationService.class.getResourceAsStream(WATERMARK_FONT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Watermark-Font-Ressource fehlt: " + WATERMARK_FONT_RESOURCE);
            }
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Watermark-Font-Ressource fehlt: " + WATERMARK_FONT_RESOURCE, ex);
        }
    }

    private static CmapLookup loadWatermarkFontCmap(byte[] fontBytes) {
        try {
            TrueTypeFont ttf = new TTFParser().parse(new RandomAccessReadBuffer(fontBytes));
            return ttf.getUnicodeCmapLookup();
        } catch (IOException ex) {
            throw new IllegalStateException("Watermark-Font konnte nicht gelesen werden", ex);
        }
    }

    /** Holds cached reflection metadata for the ELGA converter classes. */
    private static final class ElgaConverter implements Converter {
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

        @Override
        public byte[] convert(byte[] xmlBytes, Variant variant) throws Exception {
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

        try (PDDocument document = Loader.loadPDF(pdfBytes, null, null, null,
                MemoryUsageSetting.setupMixed(32 * 1024 * 1024).streamCache);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType0Font watermarkFont =
                PDType0Font.load(document, new ByteArrayInputStream(WATERMARK_FONT_BYTES), true);
            for (PDPage page : document.getPages()) {
                PDRectangle pageRect = page.getMediaBox();
                float centerX = pageRect.getLowerLeftX() + pageRect.getWidth() / 2f;
                float centerY = pageRect.getLowerLeftY() + pageRect.getHeight() / 2f;
                float fontSize = Math.max(40f, Math.min(pageRect.getWidth(), pageRect.getHeight()) / 8f);
                float textWidth = (watermarkFont.getStringWidth(safeText) / 1000f) * fontSize;
                Color color = new Color(150, 150, 150);

                try (PDPageContentStream contentStream =
                         new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
                    graphicsState.setNonStrokingAlphaConstant(watermarkOpacity);
                    graphicsState.setStrokingAlphaConstant(watermarkOpacity);
                    contentStream.setGraphicsStateParameters(graphicsState);
                    contentStream.setNonStrokingColor(color);
                    contentStream.beginText();
                    contentStream.setFont(watermarkFont, fontSize);
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
            if (WATERMARK_FONT_CMAP.getGlyphId(cp) != 0) {
                sb.appendCodePoint(cp);
            } else {
                sb.append('-');
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }
}
