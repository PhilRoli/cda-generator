package at.rolinek.cda.ai;

/**
 * The AI call failed; the message is safe to show to the user (German, no upstream details).
 * If the upstream call completed and was billed (refusal, truncation, unreadable output),
 * the tokens it used are carried along so they still count toward the budget.
 */
public class AiClientException extends Exception {

    private final long inputTokens;
    private final long outputTokens;

    public AiClientException(String userMessage) {
        this(userMessage, 0L, 0L);
    }

    public AiClientException(String userMessage, long inputTokens, long outputTokens) {
        super(userMessage);
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    public long inputTokens() {
        return inputTokens;
    }

    public long outputTokens() {
        return outputTokens;
    }
}
