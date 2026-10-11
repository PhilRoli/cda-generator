package at.rolinek.cda.api;

import at.rolinek.cda.ai.AiBudgetService;
import at.rolinek.cda.ai.AiClient;
import at.rolinek.cda.ai.AiClientException;
import at.rolinek.cda.ai.AiFillState;
import at.rolinek.cda.usage.IpPrefix;
import at.rolinek.cda.usage.UsageRecorder;
import at.rolinek.cda.usage.UsageRecordingFilter;
import at.rolinek.cda.usage.UsageType;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** AI fill (BETA): turns a short free-text case description into a complete form state. */
@RestController
@RequestMapping("/api/ai")
public class AiFillController {

    private static final Logger LOG = LoggerFactory.getLogger(AiFillController.class);

    public record FillRequest(String prompt) {}

    public record FillResponse(AiFillState state) {}

    private final AiClient client;
    private final AiBudgetService budget;
    private final UsageRecorder usageRecorder;

    public AiFillController(AiClient client, AiBudgetService budget, UsageRecorder usageRecorder) {
        this.client = client;
        this.budget = budget;
        this.usageRecorder = usageRecorder;
    }

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("enabled", budget.enabled());
    }

    @PostMapping("/fill")
    public FillResponse fill(@RequestBody(required = false) FillRequest body,
                             @RequestHeader(name = UsageRecordingFilter.USER_HEADER, required = false) String userHeader,
                             HttpServletRequest request) {
        String prompt = body == null || body.prompt() == null ? "" : body.prompt().strip();
        int max = budget.maxPromptChars();
        if (prompt.isEmpty() || prompt.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Bitte eine kurze Beschreibung eingeben (höchstens " + max + " Zeichen).");
        }
        String decoded = UsageRecordingFilter.decodeUser(userHeader);
        String user = decoded == null ? "" : decoded.strip();
        try {
            budget.checkAllowed(user, IpPrefix.of(ClientIp.from(request)));
        } catch (ResponseStatusException ex) {
            usageRecorder.record(UsageType.AI_FILL, ex.getStatusCode().value(), user, request, "", 0L, 0L);
            throw ex;
        }
        try {
            AiClient.Result result = client.fill(prompt);
            usageRecorder.record(UsageType.AI_FILL, 200, user, request, "", result.inputTokens(), result.outputTokens());
            return new FillResponse(result.state());
        } catch (AiClientException ex) {
            LOG.warn("event=ai_fill_failed reason={}", ex.getMessage());
            usageRecorder.record(UsageType.AI_FILL, 502, user, request, "", 0L, 0L);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
        }
    }
}
