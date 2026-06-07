package at.rolinek.cda.api;

import at.rolinek.cda.config.AppProperties;
import at.rolinek.cda.pdf.PdfGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the password gate on {@code POST /api/pdf/upload}.
 *
 * <p>All auth-logic checks run BEFORE the actual ELGA conversion, so the ELGA jars
 * are NOT required. {@link PdfGenerationService} is mocked throughout.
 */
@WebMvcTest(PdfController.class)
@Import(GlobalExceptionHandler.class)
class PdfControllerCleanPasswordTest {

    private static final MockMultipartFile DUMMY_XML = new MockMultipartFile(
            "file", "test.xml", "application/xml", "<ClinicalDocument/>".getBytes());

    @Autowired
    MockMvc mvc;

    @MockBean
    PdfGenerationService pdfGenerationService;

    // AppProperties is injected into PdfController — we provide it as a mock bean
    // so tests can control the configured password without touching application.properties.
    @MockBean
    AppProperties appProperties;

    // -------------------------------------------------------------------------
    // Fail-closed: password not configured → 403 regardless of request header
    // -------------------------------------------------------------------------

    @Test
    void unconfiguredPassword_noHeader_returns403NotConfigured() throws Exception {
        given(appProperties.getCleanPdfPassword()).willReturn("");

        mvc.perform(multipart("/api/pdf/upload").file(DUMMY_XML))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Funktion ist nicht konfiguriert."));
    }

    @Test
    void unconfiguredPassword_withHeader_stillReturns403NotConfigured() throws Exception {
        given(appProperties.getCleanPdfPassword()).willReturn("");

        mvc.perform(multipart("/api/pdf/upload")
                        .file(DUMMY_XML)
                        .header("X-Clean-Pdf-Password", "mbi24"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Funktion ist nicht konfiguriert."));
    }

    @Test
    void nullPassword_noHeader_returns403NotConfigured() throws Exception {
        given(appProperties.getCleanPdfPassword()).willReturn(null);

        mvc.perform(multipart("/api/pdf/upload").file(DUMMY_XML))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Funktion ist nicht konfiguriert."));
    }

    // -------------------------------------------------------------------------
    // Configured password + wrong/missing header → 403 "Ungültiges Passwort."
    // -------------------------------------------------------------------------

    @Test
    void configuredPassword_noHeader_returns403WrongPassword() throws Exception {
        given(appProperties.getCleanPdfPassword()).willReturn("mbi24");

        mvc.perform(multipart("/api/pdf/upload").file(DUMMY_XML))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Ungültiges Passwort."));
    }

    @Test
    void configuredPassword_wrongHeader_returns403WrongPassword() throws Exception {
        given(appProperties.getCleanPdfPassword()).willReturn("mbi24");

        mvc.perform(multipart("/api/pdf/upload")
                        .file(DUMMY_XML)
                        .header("X-Clean-Pdf-Password", "wrong"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Ungültiges Passwort."));
    }

    @Test
    void configuredPassword_emptyHeader_returns403WrongPassword() throws Exception {
        given(appProperties.getCleanPdfPassword()).willReturn("mbi24");

        mvc.perform(multipart("/api/pdf/upload")
                        .file(DUMMY_XML)
                        .header("X-Clean-Pdf-Password", ""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Ungültiges Passwort."));
    }

    // -------------------------------------------------------------------------
    // Configured password + correct header → reaches the service (200 OK)
    // -------------------------------------------------------------------------

    @Test
    void configuredPassword_correctHeader_proceedsToService() throws Exception {
        given(appProperties.getCleanPdfPassword()).willReturn("mbi24");
        given(pdfGenerationService.generatePdfClean(anyString()))
                .willReturn(new byte[]{0x25, 0x50, 0x44, 0x46}); // minimal %PDF magic bytes

        mvc.perform(multipart("/api/pdf/upload")
                        .file(DUMMY_XML)
                        .header("X-Clean-Pdf-Password", "mbi24"))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // Unit-level constant-time comparison sanity checks
    // -------------------------------------------------------------------------

    @Test
    void constantTimeEquals_sameValues_returnsTrue() {
        org.assertj.core.api.Assertions.assertThat(
                PdfController.constantTimeEquals("mbi24", "mbi24")).isTrue();
    }

    @Test
    void constantTimeEquals_differentValues_returnsFalse() {
        org.assertj.core.api.Assertions.assertThat(
                PdfController.constantTimeEquals("mbi24", "wrong")).isFalse();
    }

    @Test
    void constantTimeEquals_nullAndNonNull_returnsFalse() {
        org.assertj.core.api.Assertions.assertThat(
                PdfController.constantTimeEquals(null, "mbi24")).isFalse();
    }

    @Test
    void constantTimeEquals_bothNull_returnsTrue() {
        org.assertj.core.api.Assertions.assertThat(
                PdfController.constantTimeEquals(null, null)).isTrue();
    }
}
