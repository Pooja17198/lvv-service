package com.oracle.pic.networking.lvv.service.kiev;

import com.google.common.util.concurrent.RateLimiter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public class KievRateLimiter {
    private static final RateLimiter rateLimiter = RateLimiter.create(150);

    public static void throttle() {
        rateLimiter.acquire();
    }

    // Cannot access config from a static context, so will call setRate
    // from LvvServiceApi.run()
    public static void setRate(double tps) {
        rateLimiter.setRate(tps);
    }
}
