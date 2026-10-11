package at.rolinek.cda.api;

import at.rolinek.cda.ai.AiBudgetService;
import at.rolinek.cda.ai.AiClient;
import at.rolinek.cda.ai.AiClientException;
import at.rolinek.cda.ai.AiFillStateFixtures;
import at.rolinek.cda.usage.UsageRecorder;
import at.rolinek.cda.usage.UsageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiFillController.class)
@Import(GlobalExceptionHandler.class)
class AiFillControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AiClient client;
    @MockitoBean AiBudgetService budget;
    @MockitoBean UsageRecorder usageRecorder;

    @BeforeEach
    void setUp() {
        given(budget.maxPromptChars()).willReturn(1000);
    }

    private ResultActions fill(String body, String user) throws Exception {
        var req = post("/api/ai/fill").contentType(MediaType.APPLICATION_JSON).content(body);
        if (user != null) req = req.header("X-Cda-User", user);
        return mvc.perform(req);
    }

    @Test
    void successReturnsStateAndRecordsTokensForDecodedUser() throws Exception {
        given(client.fill("78-jähriger Mann, Herzinsuffizienz"))
            .willReturn(new AiClient.Result(AiFillStateFixtures.sample(), 1500, 700));

        fill("{\"prompt\":\"  78-jähriger Mann, Herzinsuffizienz  \"}", "J%C3%BCrgen")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state.patient.givenName").value("Franz"))
            .andExpect(jsonPath("$.state.medikation[0].schema").value("1-0-0"));

        verify(budget).checkAllowed(eq("Jürgen"), anyString());
        verify(usageRecorder).record(eq(UsageType.AI_FILL), eq(200), eq("Jürgen"), any(), eq(""), eq(1500L), eq(700L));
    }

    @Test
    void emptyMissingAndOversizedPromptsAre400WithoutUpstreamCall() throws Exception {
        fill("{\"prompt\":\"   \"}", null).andExpect(status().isBadRequest());
        fill("{}", null).andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/fill").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        fill("{\"prompt\":\"" + "x".repeat(1001) + "\"}", null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1000")));
        verifyNoInteractions(client);
        verify(usageRecorder, never()).record(any(), anyInt(), any(), any(), any(), anyLong(), anyLong());
    }

    @Test
    void exactlyMaxLengthPromptIsAccepted() throws Exception {
        given(client.fill("x".repeat(1000))).willReturn(new AiClient.Result(AiFillStateFixtures.sample(), 1, 1));
        fill("{\"prompt\":\"" + "x".repeat(1000) + "\"}", null).andExpect(status().isOk());
    }

    @Test
    void budgetRejectionIsRelayedAndRecordedAsThrottled() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Tageslimit für KI-Anfragen erreicht."))
            .given(budget).checkAllowed(anyString(), anyString());

        fill("{\"prompt\":\"Test\"}", "anna")
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.message").value("Tageslimit für KI-Anfragen erreicht."));

        verifyNoInteractions(client);
        verify(usageRecorder).record(eq(UsageType.AI_FILL), eq(429), eq("anna"), any(), eq(""), eq(0L), eq(0L));
    }

    @Test
    void upstreamFailureIs502WithUserMessageAndRecordedAsFailure() throws Exception {
        given(client.fill(anyString())).willThrow(new AiClientException("Die KI hat diese Anfrage abgelehnt."));

        fill("{\"prompt\":\"Test\"}", "anna")
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.message").value("Die KI hat diese Anfrage abgelehnt."));

        verify(usageRecorder).record(eq(UsageType.AI_FILL), eq(502), eq("anna"), any(), eq(""), eq(0L), eq(0L));
    }

    @Test
    void statusReflectsBudgetService() throws Exception {
        given(budget.enabled()).willReturn(true);
        mvc.perform(get("/api/ai/status")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        given(budget.enabled()).willReturn(false);
        mvc.perform(get("/api/ai/status")).andExpect(jsonPath("$.enabled").value(false));
    }
}
