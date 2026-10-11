package at.rolinek.cda.ai;

import at.rolinek.cda.config.AppProperties;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.StructuredContentBlock;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredTextBlock;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ClaudeAiClientTest {

    private final ClaudeAiClient client = new ClaudeAiClient(new AppProperties(), Clock.systemUTC());

    @SuppressWarnings("unchecked")
    @Test
    void unparsableStructuredOutputIsAUserFacingErrorThatDoesNotCarryTheModelOutput() {
        StructuredTextBlock<AiFillState> text = mock(StructuredTextBlock.class);
        given(text.text()).willThrow(new AnthropicInvalidDataException("Error parsing JSON: SECRET-MODEL-OUTPUT"));
        StructuredContentBlock<AiFillState> block = mock(StructuredContentBlock.class);
        given(block.text()).willReturn(Optional.of(text));
        StructuredMessage<AiFillState> response = mock(StructuredMessage.class);
        given(response.stopReason()).willReturn(Optional.empty());
        given(response.content()).willReturn(List.of(block));

        assertThatThrownBy(() -> client.interpret(response))
            .isInstanceOf(AiClientException.class)
            .hasMessageContaining("keine verwertbare Antwort")
            .hasMessageNotContaining("SECRET");
    }

    @Test
    void upstreamErrorsAreDescribedByTypeAndStatusOnly() {
        AnthropicServiceException ex = mock(AnthropicServiceException.class);
        given(ex.statusCode()).willReturn(429);
        given(ex.getMessage()).willReturn("429: {\"error\":\"SECRET-UPSTREAM-BODY\"}");

        String described = ClaudeAiClient.describe(ex);

        assertThat(described).contains("status=429").doesNotContain("SECRET");
    }

    @Test
    void nonServiceErrorsAreDescribedByTypeOnly() {
        assertThat(ClaudeAiClient.describe(new AnthropicInvalidDataException("SECRET")))
            .isEqualTo("AnthropicInvalidDataException")
            .doesNotContain("SECRET");
    }
}
