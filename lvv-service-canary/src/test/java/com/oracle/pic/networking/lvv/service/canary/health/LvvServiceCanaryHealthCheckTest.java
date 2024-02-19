package com.oracle.pic.networking.lvv.service.canary.health;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codahale.metrics.health.HealthCheck.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LvvServiceCanaryHealthCheckTest {

    private LvvServiceCanaryHealthCheck health;

    @BeforeEach
    public void setup() {
        health = new LvvServiceCanaryHealthCheck();
    }

    @Test
    public void testCheck() throws Exception {
        Result result = health.check();

        assertTrue(!result.isHealthy());
    }
}
