package com.proxy.design.pattern.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ReportServiceProxyTest {

    private final AtomicInteger calls = new AtomicInteger();

    private ReportServiceProxy proxy(Clock clock) {
        ReportService implementation = topic -> "report-" + calls.incrementAndGet() + ":" + topic;
        return new ReportServiceProxy(implementation, clock);
    }

    @Test
    void cacheHitDoesNotRegenerateReport() {
        ReportServiceProxy proxy = proxy(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        assertEquals("report-1:java", proxy.generateReport(" java "));
        assertEquals("report-1:java", proxy.generateReport("java"));
        assertEquals(1, calls.get());
    }

    @Test
    void twentiethRequestAllowedAndTwentyFirstReturns429() {
        ReportServiceProxy proxy = proxy(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        for (int i = 0; i < 20; i++) {
            assertNotNull(proxy.generateReport("topic" + i));
        }

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class, () -> proxy.generateReport("extra"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        assertEquals(20, calls.get());
    }

    @Test
    void limiterResetsAfterOneMinute() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        ReportServiceProxy proxy = proxy(clock);
        for (int i = 0; i < 20; i++) proxy.generateReport("topic" + i);

        clock.advanceSeconds(60);
        assertEquals("report-21:next", proxy.generateReport("next"));
    }

    @Test
    void cacheExpiresAfterFiveMinutes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        ReportServiceProxy proxy = proxy(clock);

        assertEquals("report-1:java", proxy.generateReport("java"));
        clock.advanceSeconds(300);
        assertEquals("report-2:java", proxy.generateReport("java"));
    }

    @Test
    void evictsOldestEntryWhenCacheExceedsOneHundredTopics() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        ReportServiceProxy proxy = proxy(clock);

        assertEquals("report-1:old", proxy.generateReport("old"));
        for (int i = 0; i < 100; i++) {
            // Stay under the fixed-window request budget while filling the cache.
            if (i % 19 == 0) clock.advanceSeconds(60);
            proxy.generateReport("unique-" + i);
        }
        assertEquals("report-102:old", proxy.generateReport("old"));
    }

    @Test
    void rejectsBlankTopicsWith400() {
        ReportServiceProxy proxy = proxy(Clock.systemUTC());
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class, () -> proxy.generateReport("   "));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
