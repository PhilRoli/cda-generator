package at.rolinek.cda.api;

import at.rolinek.cda.usage.UsageRecorder;
import at.rolinek.cda.usage.UsageType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsageController.class)
@Import(GlobalExceptionHandler.class)
class UsageControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UsageRecorder usageRecorder;

    @Test
    void xmlDownloadIsRecordedWithUsername() throws Exception {
        mvc.perform(post("/api/usage/xml-download").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"anna\"}"))
            .andExpect(status().isNoContent());
        verify(usageRecorder).record(eq(UsageType.XML_DOWNLOAD), eq(200), eq("anna"), any(), eq(""));
    }

    @Test
    void xmlDownloadWithoutBodyIsRecordedAnonymously() throws Exception {
        mvc.perform(post("/api/usage/xml-download"))
            .andExpect(status().isNoContent());
        verify(usageRecorder).record(eq(UsageType.XML_DOWNLOAD), eq(200), eq(null), any(), eq(""));
    }
}
