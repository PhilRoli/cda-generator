package at.rolinek.cda.ai;

public interface AiClient {

    record Result(AiFillState state, long inputTokens, long outputTokens) {}

    Result fill(String prompt) throws AiClientException;
}
