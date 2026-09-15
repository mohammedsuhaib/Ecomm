package com.townbasket.shared;

/**
 * The caller has to wait before trying again -> {@code 429} with a
 * {@code Retry-After} header (see {@code GlobalExceptionHandler}).
 *
 * <p>Deliberately distinct from {@link BusinessRuleException}: nothing about the
 * request is wrong, so the client should retry the very same call later rather
 * than change it. Thrown from inside the application (e.g. the staff-login
 * failure throttle); the edge limiter on {@code /api/v1/auth/*} is a servlet
 * filter that writes its own 429 because it runs outside the advice.
 */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    /** Seconds the client should wait, for the {@code Retry-After} header. */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
