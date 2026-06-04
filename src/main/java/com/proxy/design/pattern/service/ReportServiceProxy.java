package com.proxy.design.pattern.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ReportServiceProxy implements ReportService {

    private final ReportServiceImpl realService;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private final AtomicInteger requestCount =
            new AtomicInteger(0);

    public ReportServiceProxy(ReportServiceImpl realService) {
        this.realService = realService;
    }

    @Override
    public String generateReport(String topic) {

        System.out.println("Request received: " + topic);

        if (requestCount.incrementAndGet() > 20) {
            throw new RuntimeException(
                    "Rate limit exceeded");
        }

        if (cache.containsKey(topic)) {

            System.out.println(
                    "Returning cached report");

            return cache.get(topic);
        }

        String report =
                realService.generateReport(topic);

        cache.put(topic, report);

        return report;
    }
}
