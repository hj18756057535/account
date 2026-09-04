package com.hypers.account.web;

import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.web.management.ManagementApplicationController.ApplicationResponse;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 只读兼容入口；仅返回脱敏管理 DTO，不暴露签名密钥。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountDirectoryController {

    private final AccountDirectoryService directoryService;

    @GetMapping("/users/{userId}/applications")
    public List<ApplicationResponse> getUserApplications(@PathVariable String userId) {
        return directoryService.getUserAuthorizedApplications(userId).stream()
                .map(ApplicationResponse::from)
                .collect(Collectors.toList());
    }
}
