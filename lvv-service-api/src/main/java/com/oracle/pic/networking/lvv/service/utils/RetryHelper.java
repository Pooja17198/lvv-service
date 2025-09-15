package com.oracle.pic.networking.lvv.service.utils;

import com.oracle.bmc.model.BmcException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

// FIXME: This entire class handling of exceptions is incredibly unsafe...
@Slf4j
@AllArgsConstructor(staticName = "newRetryHelper", access = AccessLevel.PRIVATE)
@ToString
@Builder
public class RetryHelper<T> {

    @FunctionalInterface
    public interface RetryableFunction<T> {
        T run() throws Exception;
    }

    @NonNull private final RetryableFunction<T> function;
    private final int maxTries;
    private final double jitterFactor;
    private final double exponentialFactor;
    @NonNull private final Duration initialRetrySleepTime;
    @NonNull private final Duration maxRetrySleepTime;
    private final Predicate<Exception> nonRetryablePredicate;

    /**
     * Provides a retry helper with default values jitterFactor = 20% exponentialFactor = 2
     * initialRetrySleepTime = 1 second maxRetrySleepTime = 5 seconds
     *
     * @param function The function to try
     * @param maxTries Maximum number of tries
     * @param nonRetryablePredicate Predicate to identify non-retryable exceptions
     */
    public static <T> RetryHelper<T> newRetryHelper(
            RetryableFunction<T> function,
            int maxTries,
            Predicate<Exception> nonRetryablePredicate) {
        double jitterFactor = 1.0;
        double exponentialFactor = 2.0;
        Duration initialRetrySleepTime = Duration.ofSeconds(1);
        Duration maxRetrySleepTime = Duration.ofSeconds(5);

        return new RetryHelper<T>(
                function,
                maxTries,
                jitterFactor,
                exponentialFactor,
                initialRetrySleepTime,
                maxRetrySleepTime,
                nonRetryablePredicate);
    }

    public T run() throws Exception {
        Exception toThrow = null;
        for (int tryCount = 1; tryCount <= maxTries; ++tryCount) {
            try {
                return function.run();
            } catch (Exception e) {
                toThrow = e;
                if (nonRetryablePredicate != null && nonRetryablePredicate.test(e)) {
                    log.error(
                            "Non-retryable predicate encountered. Bailing out after {} tries of {}",
                            tryCount,
                            function,
                            e);
                    break;
                }
                if (tryCount != maxTries) {
                    double base =
                            initialRetrySleepTime.toMillis()
                                    * Math.pow(exponentialFactor, tryCount - 1);
                    double cappedBase = Math.min(maxRetrySleepTime.toMillis(), base);

                    log.error(
                            "Will retry after sleep for function {} due to error: ",
                            function.getClass().getSimpleName(),
                            e);
                    GeneralUtils.jitterSleep(
                            Duration.ofMillis(Math.round(cappedBase)), jitterFactor);
                }
            }
        }
        throw toThrow;
    }

    public T runUnchecked() {
        try {
            return run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<T> runAndSwallowExceptions() {
        try {
            return Optional.ofNullable(run());
        } catch (Exception e) {
            log.error("Swallowing exception per retry policy of {} in {}", function, this, e);
            return Optional.empty();
        }
    }

    // Predicates for RetryHelper should return true if the exception is non retryable
    public static Predicate<Exception> allButClientErrors =
            e -> {
                // Retry everything but client errors
                if (e instanceof BmcException) {
                    int httpCode = ((BmcException) e).getStatusCode();
                    return httpCode >= 400 && httpCode < 500;
                }
                return false;
            };

    // Assume ssh error is always retryable
    public static Predicate<Exception> retryAll = null;
}
