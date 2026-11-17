package com.proxy.design.pattern.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Proxy adds a per-instance request limit and bounded, expiring cache
 * without changing the ReportService contract.
 *
 * This is deliberately in-memory: limits and cache are NOT shared across
 * application instances. Production distributed limits need a shared store.
 */
@Service
public class ReportServiceProxy implements ReportService {

    private static final int MAX_REQUESTS = 20;
    private static final int MAX_CACHE_ENTRIES = 100;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final ReportService realService;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    private Instant windowStart;
    private int windowRequests;

    public ReportServiceProxy(ReportServiceImpl realService) {
        this(realService, Clock.systemUTC());
    }

    // Package-private seam for deterministic tests, without changing Spring wiring.
    ReportServiceProxy(ReportService realService, Clock clock) {
        this.realService = realService;
        this.clock = clock;
        this.windowStart = clock.instant();
    }

    @Override
    public synchronized String generateReport(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report topic must not be blank");
        }
        String normalizedTopic = topic.trim();
        Instant now = clock.instant();

        // Use a new window after 60 seconds (also handle backward clock changes).
        if (now.isBefore(windowStart) ||
                !now.isBefore(windowStart.plus(RATE_LIMIT_WINDOW))) {
            windowStart = now;
            windowRequests = 0;
        }

        if (windowRequests >= MAX_REQUESTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded: 20 requests per minute");
        }
        windowRequests++;

        CacheEntry cached = cache.get(normalizedTopic);
        if (cached != null && now.isBefore(cached.createdAt().plus(CACHE_TTL))) {
            return cached.value();
        }

        // Holding the lock while loading ensures simultaneous callers don't
        // generate the same report multiple times; simple but serializes misses.
        String result = realService.generateReport(normalizedTopic);
        cache.put(normalizedTopic, new CacheEntry(result, now));
        return result;
    }

    private record CacheEntry(String value, Instant createdAt) {}
}
