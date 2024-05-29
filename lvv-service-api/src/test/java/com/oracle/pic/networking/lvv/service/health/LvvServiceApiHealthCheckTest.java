package com.oracle.pic.networking.lvv.service.health;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codahale.metrics.health.HealthCheck.Result;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LvvServiceApiHealthCheckTest {

    private LvvServiceApiHealthCheck health;

    @BeforeEach
    public void setup() {
        health = new LvvServiceApiHealthCheck();
    }

    @Test
    public void testCheck() throws Exception {
        Result result = health.check();

        assertTrue(result.isHealthy());
    }

    @Test
    void getName() {
        Assertions.assertEquals(LvvServiceApiHealthCheck.NAME, LvvServiceApiHealthCheck.getName());
    }

    @Test
    void check() throws Exception {
        Assertions.assertEquals(Result.healthy(), health.check());
    }
}
