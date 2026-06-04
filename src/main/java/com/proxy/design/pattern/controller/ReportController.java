package com.proxy.design.pattern.controller;

import com.proxy.design.pattern.service.ReportServiceProxy;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
@CrossOrigin("*")
public class ReportController {

    private final ReportServiceProxy proxy;

    public ReportController(
            ReportServiceProxy proxy) {

        this.proxy = proxy;
    }

    @GetMapping
    public String generate(
            @RequestParam String topic) {

        return proxy.generateReport(topic);
    }
}