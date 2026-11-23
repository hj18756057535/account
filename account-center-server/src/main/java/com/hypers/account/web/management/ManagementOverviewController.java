package com.hypers.account.web.management;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "account.console-api.enabled", havingValue = "true", matchIfMissing = true)
public class ManagementOverviewController {
    private final ManagementOverviewService service;

    @GetMapping("/api/overview")
    public ManagementOverviewService.Overview overview() {
        return service.overview();
    }
}
