package com.example.decision.dependency;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SimulatedVerificationService {

    private static final Logger log = LoggerFactory.getLogger(SimulatedVerificationService.class);

    public enum FailureMode {
        OK,
        FAIL_ALWAYS,
        FAIL_FIRST_N
    }

    @Value("${dependency.verification.mode:OK}")
    private String configuredMode;

    @Value("${dependency.verification.fail-count:0}")
    private int configuredFailCount;

    private volatile FailureMode mode = FailureMode.OK;
    private volatile int failCount = 0;
    private final AtomicInteger callCount = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        try {
            this.mode = FailureMode.valueOf(configuredMode.toUpperCase());
        } catch (Exception e) {
            this.mode = FailureMode.OK;
        }
        this.failCount = configuredFailCount;
    }

    public synchronized void setMode(FailureMode mode, int failCount) {
        this.mode = mode;
        this.failCount = failCount;
        this.callCount.set(0);
        log.info("SimulatedVerificationService mode updated to: {}, failCount: {}", mode, failCount);
    }

    public synchronized void setMode(FailureMode mode) {
        setMode(mode, 0);
    }

    public void resetCallCount() {
        this.callCount.set(0);
    }

    public int getCallCount() {
        return callCount.get();
    }

    public FailureMode getMode() {
        return mode;
    }

    /**
     * Simulates external verification call.
     * Throws ExternalDependencyException if the configured failure mode triggers.
     */
    public void verify(String workflow, String requestId, Map<String, Object> payload) {
        int current = callCount.incrementAndGet();
        log.info("Verification call #{} for workflow='{}', requestId='{}' under mode={}",
                current, workflow, requestId, mode);

        if (mode == FailureMode.FAIL_ALWAYS) {
            throw new ExternalDependencyException("Simulated external verification failed (FAIL_ALWAYS)");
        }

        if (mode == FailureMode.FAIL_FIRST_N) {
            if (current <= failCount) {
                throw new ExternalDependencyException(String.format(
                        "Simulated external verification failed (transient failure %d of %d)", current, failCount));
            }
        }

        // Mode is OK or transient failures have passed
        log.info("Verification successful for requestId='{}'", requestId);
    }
}

