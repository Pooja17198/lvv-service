package com.oracle.pic.networking.lvv.service.utils;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.oracle.bmc.model.BmcException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RetryHelperTest {

    @Mock private RetryHelper.RetryableFunction<String> function;

    @Mock private Predicate<Exception> nonRetryablePredicate;

    private int maxTries = 3;

    @Before
    public void setUp() {
        // Reset mocks before each test
        reset(function, nonRetryablePredicate);
    }

    @Test
    public void testNewRetryHelperWithDefaults() throws Exception {
        RetryHelper<String> retryHelper =
                RetryHelper.newRetryHelper(function, maxTries, nonRetryablePredicate);
        when(function.run()).thenReturn("success");

        String result = retryHelper.run();

        assertEquals("success", result);
        verify(function, times(1)).run();
        verifyNoInteractions(nonRetryablePredicate);
    }

    @Test
    public void testRunSuccessWithoutRetries() throws Exception {
        RetryHelper<String> retryHelper =
                RetryHelper.newRetryHelper(function, maxTries, nonRetryablePredicate);
        when(function.run()).thenReturn("success");

        String result = retryHelper.run();

        assertEquals("success", result);
        verify(function, times(1)).run();
        verifyNoInteractions(nonRetryablePredicate);
        try (MockedStatic<GeneralUtils> mockedUtils = mockStatic(GeneralUtils.class)) {
            mockedUtils.verify(
                    () -> GeneralUtils.jitterSleep(any(Duration.class), anyDouble()), never());
        }
    }

    @Test
    public void testRunWithRetryableException() throws Exception {
        RetryHelper<String> retryHelper =
                RetryHelper.newRetryHelper(function, maxTries, nonRetryablePredicate);
        when(function.run()).thenThrow(new RuntimeException("retryable")).thenReturn("success");
        when(nonRetryablePredicate.test(any(Exception.class))).thenReturn(false);

        try (MockedStatic<GeneralUtils> mockedUtils = mockStatic(GeneralUtils.class)) {
            String result = retryHelper.run();

            assertEquals("success", result);
            verify(function, times(2)).run();
            verify(nonRetryablePredicate, times(1)).test(any(Exception.class));
            mockedUtils.verify(
                    () -> GeneralUtils.jitterSleep(Duration.ofMillis(1000), 1.0), times(1));
        }
    }

    @Test
    public void testRunWithNonRetryableException() throws Exception {
        RetryHelper<String> retryHelper =
                RetryHelper.newRetryHelper(function, maxTries, nonRetryablePredicate);
        Exception nonRetryable = new RuntimeException("non-retryable");
        when(function.run()).thenThrow(nonRetryable);
        when(nonRetryablePredicate.test(nonRetryable)).thenReturn(true);

        try (MockedStatic<GeneralUtils> mockedUtils = mockStatic(GeneralUtils.class)) {
            try {
                retryHelper.run();
                fail("Expected RuntimeException");
            } catch (Exception e) {
                assertEquals(nonRetryable, e);
            }
            verify(function, times(1)).run();
            verify(nonRetryablePredicate, times(1)).test(nonRetryable);
            mockedUtils.verify(
                    () -> GeneralUtils.jitterSleep(any(Duration.class), anyDouble()), never());
        }
    }

    @Test
    public void testRunMaxTriesExceeded() throws Exception {
        RetryHelper<String> retryHelper =
                RetryHelper.newRetryHelper(function, maxTries, nonRetryablePredicate);
        Exception retryable = new RuntimeException("retryable");
        when(function.run()).thenThrow(retryable);
        when(nonRetryablePredicate.test(retryable)).thenReturn(false);

        try (MockedStatic<GeneralUtils> mockedUtils = mockStatic(GeneralUtils.class)) {
            try {
                retryHelper.run();
                fail("Expected RuntimeException");
            } catch (Exception e) {
                assertEquals(retryable, e);
            }
            verify(function, times(maxTries)).run();
            verify(nonRetryablePredicate, times(maxTries)).test(retryable);
            mockedUtils.verify(
                    () -> GeneralUtils.jitterSleep(Duration.ofMillis(1000), 1.0), times(1));
            mockedUtils.verify(
                    () -> GeneralUtils.jitterSleep(Duration.ofMillis(2000), 1.0), times(1));
        }
    }

    @Test
    public void testRunUncheckedSuccess() throws Exception {
        RetryHelper<String> retryHelper =
                RetryHelper.newRetryHelper(function, maxTries, nonRetryablePredicate);
        when(function.run()).thenReturn("success");

        String result = retryHelper.runUnchecked();

        assertEquals("success", result);
        verify(function, times(1)).run();
        verifyNoInteractions(nonRetryablePredicate);
    }

    @Test
    public void testRunUncheckedThrowsRuntimeException() throws Exception {
        RetryHelper<String> retryHelper =
                RetryHelper.newRetryHelper(function, maxTries, nonRetryablePredicate);
        Exception original = new Exception("error");
        when(function.run()).thenThrow(original);
        when(nonRetryablePredicate.test(original)).thenReturn(true);

        try {
            retryHelper.runUnchecked();
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals(original, e.getCause());
        }
        verify(function, times(1)).run();
        verify(nonRetryablePredicate, times(1)).test(original);
    }

    @Test
    public void testRunAndSwallowExceptionsSuccess() throws Exception {
        RetryHelper<String> retryHelper =
                RetryHelper.newRetryHelper(function, maxTries, nonRetryablePredicate);
        when(function.run()).thenReturn("success");

        Optional<String> result = retryHelper.runAndSwallowExceptions();

        assertTrue(result.isPresent());
        assertEquals("success", result.get());
        verify(function, times(1)).run();
        verifyNoInteractions(nonRetryablePredicate);
    }

    @Test
    public void testRunAndSwallowExceptionsFailure() throws Exception {
        RetryHelper<String> retryHelper =
                RetryHelper.newRetryHelper(function, maxTries, nonRetryablePredicate);
        Exception original = new Exception("error");
        when(function.run()).thenThrow(original);
        when(nonRetryablePredicate.test(original)).thenReturn(true);

        Optional<String> result = retryHelper.runAndSwallowExceptions();

        assertFalse(result.isPresent());
        verify(function, times(1)).run();
        verify(nonRetryablePredicate, times(1)).test(original);
    }

    @Test
    public void testAllButClientErrorsPredicate() {
        Predicate<Exception> predicate = RetryHelper.allButClientErrors;

        // Test BmcException with client error (400-499)
        BmcException clientError = new BmcException(400, "BadRequest", "Bad request", "requestId");
        assertTrue(predicate.test(clientError));

        // Test BmcException with server error (500)
        BmcException serverError =
                new BmcException(500, "ServerError", "Server error", "requestId");
        assertFalse(predicate.test(serverError));

        // Test non-BmcException
        Exception otherException = new RuntimeException("Other");
        assertFalse(predicate.test(otherException));
    }

    @Test
    public void testRetryAllPredicate() throws Exception {
        RetryHelper<String> retryHelper =
                RetryHelper.newRetryHelper(function, maxTries, RetryHelper.retryAll);
        Exception retryable = new RuntimeException("retryable");
        when(function.run()).thenThrow(retryable);

        try (MockedStatic<GeneralUtils> mockedUtils = mockStatic(GeneralUtils.class)) {
            try {
                retryHelper.run();
                fail("Expected RuntimeException");
            } catch (Exception e) {
                assertEquals(retryable, e);
            }
            verify(function, times(maxTries)).run();
            mockedUtils.verify(
                    () -> GeneralUtils.jitterSleep(Duration.ofMillis(1000), 1.0), times(1));
            mockedUtils.verify(
                    () -> GeneralUtils.jitterSleep(Duration.ofMillis(2000), 1.0), times(1));
        }
    }

    @Test
    public void testBuilderWithCustomValues() throws Exception {
        RetryHelper<String> retryHelper =
                RetryHelper.<String>builder()
                        .function(() -> "success")
                        .maxTries(2)
                        .jitterFactor(0.5)
                        .exponentialFactor(1.5)
                        .initialRetrySleepTime(Duration.ofMillis(500))
                        .maxRetrySleepTime(Duration.ofSeconds(2))
                        .nonRetryablePredicate(e -> true)
                        .build();

        String result = retryHelper.run();
        assertEquals("success", result);
    }
}
