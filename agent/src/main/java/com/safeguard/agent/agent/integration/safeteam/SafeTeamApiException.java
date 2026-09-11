package com.safeguard.agent.agent.integration.safeteam;

public class SafeTeamApiException extends RuntimeException {
    private final int statusCode;
    private final boolean retryable;

    public SafeTeamApiException(String message, int statusCode, boolean retryable) {
        super(message);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public SafeTeamApiException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.statusCode = 0;
        this.retryable = retryable;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
