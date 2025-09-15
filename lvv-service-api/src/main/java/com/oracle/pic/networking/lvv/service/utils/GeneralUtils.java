package com.oracle.pic.networking.lvv.service.utils;

import com.oracle.pic.commons.util.Region;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;

public class GeneralUtils {

    private static final SecureRandom RAND = new SecureRandom();

    public static long calcJitterSleepTimeMillis(Duration duration, double jitterFactor) {
        double jittered =
                duration.toMillis() * (1.0 + jitterFactor * (2 * RAND.nextDouble() - 1.0));
        return Math.round(jittered);
    }

    public static void jitterSleep(Duration duration, double jitterFactor)
            throws InterruptedException {
        Thread.sleep(calcJitterSleepTimeMillis(duration, jitterFactor));
    }

    public static String getRegionFromBuilding(String building) {
        try {
            if (building != null && building.length() >= 3) {
                Region region = Region.fromAirportCode(building.substring(0, 3));
                return region.getPublicRegionName();
            } else {
                return "";
            }
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    public static String inputStreamToString(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
}
