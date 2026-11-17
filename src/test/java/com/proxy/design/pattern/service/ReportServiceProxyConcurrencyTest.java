package com.proxy.design.pattern.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ReportServiceProxyConcurrencyTest {

    @Test
    void concurrentRequestsShareOneLoadForIdenticalTopics() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        ReportService actual = topic -> {
            loads.incrementAndGet();
            return "generated:" + topic;
        };
        ReportServiceProxy proxy = new ReportServiceProxy(actual,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        int callers = 12; // Stay below the per-window request limit.
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!go.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Other callers did not start");
                    }
                    return proxy.generateReport("topic");
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            for (Future<String> result : results) {
                assertEquals("generated:topic", result.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, loads.get(), "The proxy must not generate duplicate work");
        } finally {
            go.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failingReportIsNotCached() {
        AtomicInteger attempts = new AtomicInteger();
        ReportService actual = topic -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("Transient report failure");
            }
            return "retry succeeded";
        };
        ReportServiceProxy proxy = new ReportServiceProxy(actual,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, () -> proxy.generateReport("same"));
        assertEquals("retry succeeded", proxy.generateReport("same"));
        assertEquals("retry succeeded", proxy.generateReport("same"));
        assertEquals(2, attempts.get());
    }
}
