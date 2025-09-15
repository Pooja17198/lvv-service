package com.oracle.pic.networking.lvv.service.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import org.junit.Test;

public class GeneralUtilsTest {

    @Test
    public void testCalcJitterSleepTimeMillis() {
        Duration duration = Duration.ofMillis(1000);
        long result = GeneralUtils.calcJitterSleepTimeMillis(duration, 0.0);
        assertEquals(1000L, result);

        long jitteredResult = GeneralUtils.calcJitterSleepTimeMillis(duration, 0.1);
        assertTrue(jitteredResult > 0);
    }

    @Test
    public void testJitterSleep() {
        assertDoesNotThrow(() -> GeneralUtils.jitterSleep(Duration.ofMillis(1), 0.0));
    }
}
