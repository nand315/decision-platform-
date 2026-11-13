package com.example.decision.store;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TestFaultInjector {

    private volatile boolean enabled = false;
    private volatile int triggerOnWrite = -1;
    private final AtomicInteger writeCounter = new AtomicInteger(0);

    public void enableOnWrite(int writeNumber) {
        this.enabled = true;
        this.triggerOnWrite = writeNumber;
        this.writeCounter.set(0);
    }

    public void reset() {
        this.enabled = false;
        this.triggerOnWrite = -1;
        this.writeCounter.set(0);
    }

    public void checkFault() {
        if (!enabled) {
            return;
        }
        int count = writeCounter.incrementAndGet();
        if (count == triggerOnWrite) {
            throw new RuntimeException("Simulated fault injection during database write #" + count);
        }
    }
}

