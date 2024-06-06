package com.oracle.pic.networking.lvv.service.canary.executers;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.pic.networking.lvv.service.canary.config.LvvServiceCanaryConfiguration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/** CanaryTaskExecutor is responsible to execute canary task asynchronously */
public class CanaryTaskExecutor {

    private final ExecutorService executorService;
    private final LvvServiceCanaryConfiguration config;
    private final BasicAuthenticationDetailsProvider authenticationDetailsProvider;

    @Inject
    public CanaryTaskExecutor(
            LvvServiceCanaryConfiguration config,
            @Named("user1") BasicAuthenticationDetailsProvider authProvider) {
        executorService = Executors.newCachedThreadPool();
        this.config = config;
        this.authenticationDetailsProvider = authProvider;
    }

    public void execute() throws Exception {
        log.info("Beginning canary test");
        // TODO: Add tests.
    }

    public void shutdown() {
        executorService.shutdownNow();

        try {
            executorService.awaitTermination(120, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.out.println("Executor shutdown interrupted");
            e.printStackTrace();
        }
    }
}
