package at.rolinek.cda.ai;

/** The AI call failed; the message is safe to show to the user (German, no upstream details). */
public class AiClientException extends Exception {
    public AiClientException(String userMessage) {
        super(userMessage);
    }

    public AiClientException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }
}
