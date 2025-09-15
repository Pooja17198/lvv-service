package com.oracle.pic.networking.lvv.service.kiev;

import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.oracle.pic.kiev.DataStoreConfig;
import com.oracle.pic.kiev.mapping.MappedDataStore;
import com.oracle.pic.kiev.retryable.RetryPolicy;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
@Singleton
@ToString
public class DataStoreProvider implements Closeable, Provider<MappedDataStore> {

    private MappedDataStore mappedDataStore;

    private static final int KIEV_RETRY_ATTEMPTS = 10;
    private static final int MAXIMUM_WAIT_TIME = 3000;
    private static final int MULTIPLIER = 100;

    // Exponential wait time up to 3000ms(MAXIMUM_WAIT_TIME) with exponential multiplier of
    // 100(MULTIPLIER).
    private static final RetryPolicy KIEV_RETRY_POLICY =
            RetryPolicy.builder()
                    .waitJitterPercent(20)
                    .waitStrategy(
                            WaitStrategies.exponentialWait(
                                    MULTIPLIER, MAXIMUM_WAIT_TIME, TimeUnit.MILLISECONDS))
                    .stopStrategy(StopStrategies.stopAfterAttempt(KIEV_RETRY_ATTEMPTS))
                    .retryListener(RetryPolicy.LOGGING_LISTENER)
                    .build();

    @Inject
    public DataStoreProvider(DataStoreConfig storeConfig) throws Exception {
        storeConfig.initialize();
        String storeName = storeConfig.getStoreName();
        log.info("Kiev store name {}", storeName);

        this.mappedDataStore = new MappedDataStore(storeConfig.connect(), KIEV_RETRY_POLICY);
    }

    @Override
    public void close() throws IOException {
        mappedDataStore.close();
    }

    @Override
    public MappedDataStore get() {
        return mappedDataStore;
    }
}
