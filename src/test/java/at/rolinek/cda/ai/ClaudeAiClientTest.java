package at.rolinek.cda.ai;

import at.rolinek.cda.config.AppProperties;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredContentBlock;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.Usage;
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
    private StructuredMessage<AiFillState> response(Optional<StopReason> stop) {
        Usage usage = mock(Usage.class);
        given(usage.inputTokens()).willReturn(120L);
        given(usage.outputTokens()).willReturn(4000L);
        StructuredMessage<AiFillState> response = mock(StructuredMessage.class);
        given(response.stopReason()).willReturn(stop);
        given(response.usage()).willReturn(usage);
        return response;
    }

    @SuppressWarnings("unchecked")
    @Test
    void unparsableStructuredOutputIsAUserFacingErrorThatDoesNotCarryTheModelOutput() {
        StructuredTextBlock<AiFillState> text = mock(StructuredTextBlock.class);
        given(text.text()).willThrow(new AnthropicInvalidDataException("Error parsing JSON: SECRET-MODEL-OUTPUT"));
        StructuredContentBlock<AiFillState> block = mock(StructuredContentBlock.class);
        given(block.text()).willReturn(Optional.of(text));
        StructuredMessage<AiFillState> response = response(Optional.empty());
        given(response.content()).willReturn(List.of(block));

        assertThatThrownBy(() -> client.interpret(response))
            .isInstanceOf(AiClientException.class)
            .hasMessageContaining("keine verwertbare Antwort")
            .hasMessageNotContaining("SECRET");
    }

    @Test
    void billedTokensOfARefusedResponseTravelWithTheException() {
        StructuredMessage<AiFillState> response = response(Optional.of(StopReason.REFUSAL));

        assertThatThrownBy(() -> client.interpret(response))
            .isInstanceOfSatisfying(AiClientException.class, ex -> {
                assertThat(ex.getMessage()).contains("abgelehnt");
                assertThat(ex.inputTokens()).isEqualTo(120L);
                assertThat(ex.outputTokens()).isEqualTo(4000L);
            });
    }

    @Test
    void billedTokensOfATruncatedResponseTravelWithTheException() {
        StructuredMessage<AiFillState> response = response(Optional.of(StopReason.MAX_TOKENS));

        assertThatThrownBy(() -> client.interpret(response))
            .isInstanceOfSatisfying(AiClientException.class, ex -> {
                assertThat(ex.getMessage()).contains("unvollständig");
                assertThat(ex.inputTokens()).isEqualTo(120L);
                assertThat(ex.outputTokens()).isEqualTo(4000L);
            });
    }

    @SuppressWarnings("unchecked")
    @Test
    void billedTokensOfAnUnparsableResponseTravelWithTheException() {
        StructuredTextBlock<AiFillState> text = mock(StructuredTextBlock.class);
        given(text.text()).willThrow(new AnthropicInvalidDataException("bad"));
        StructuredContentBlock<AiFillState> block = mock(StructuredContentBlock.class);
        given(block.text()).willReturn(Optional.of(text));
        StructuredMessage<AiFillState> response = response(Optional.empty());
        given(response.content()).willReturn(List.of(block));

        assertThatThrownBy(() -> client.interpret(response))
            .isInstanceOfSatisfying(AiClientException.class, ex -> assertThat(ex.outputTokens()).isEqualTo(4000L));
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
