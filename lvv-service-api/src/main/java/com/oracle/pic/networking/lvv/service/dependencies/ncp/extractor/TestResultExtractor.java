package com.oracle.pic.networking.lvv.service.dependencies.ncp.extractor;

import com.oracle.pic.commons.metrics.MetricsScope;
import java.util.List;
import java.util.Map;

/**
 * Strategy interface for per-test result extraction.
 *
 * <p>Implementations encapsulate parsing of a specific test&#39;s result payload and
 * populate/augment the shared results map with ValidationFailureResult.Builder entries, keyed by
 * device/port info.
 *
 * <p>Notes: - deviceId is the device under test. - message is the test-specific message (usually
 * present when the test FAILED). - lldpTestPassed allows extractors with cross-test dependency
 * (e.g., optics, power) to set LLDP status. - scope allows implementations to emit metrics when
 * encountering unexpected formats, etc. - results is the shared accumulator keyed by
 * NcpJobResultProcessor.DevicePortInfo.
 *
 * <p>This interface is designed to be used without modifying NcpJobResultProcessor immediately.
 * Implementations can be wired later and called from the processor.
 */
public interface TestResultExtractor {
    /**
     * @return the test case name this extractor handles, e.g. "test_lldp", "test_optics",
     *     "test_power".
     */
    String testName();

    /**
     * Perform extraction for a single device&#39;s test result.
     *
     * @param deviceId the device identifier
     * @param message the raw test message (may be null for some tests like power)
     * @param scope metrics scope for emitting counters on format issues, etc.
     * @param deviceResults shared map to accumulate results of the NCP job
     */
    void extract(
            String deviceId,
            String message,
            MetricsScope scope,
            Map<String, Map<String, List<Map<String, String>>>> deviceResults);
}
