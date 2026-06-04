package com.proxy.design.pattern.service;

import org.springframework.stereotype.Service;

@Service
public class ReportServiceImpl implements ReportService {

    @Override
    public String generateReport(String topic) {

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return "Generated report for: " + topic;
    }
}
