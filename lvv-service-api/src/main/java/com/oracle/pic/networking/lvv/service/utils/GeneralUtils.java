package com.oracle.pic.networking.lvv.service.utils;

import com.oracle.pic.commons.util.Region;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Objects;

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

    // In grafana for us-phoenix-1 the name used to filter is r2
    public static String getRegionInternalName(String publicName) {
        if (publicName == null || publicName.isEmpty()) {
            return null;
        }
        if (Objects.equals(publicName, "us-phoenix-1")) {
            return "r2";
        }
        return publicName;
    }
}
