package com.hypers.account.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/applications")
    public String applications() {
        return "redirect:/console/applications";
    }

    @GetMapping("/audit-logs")
    public String auditLogs() {
        return "audit-logs";
    }
}
