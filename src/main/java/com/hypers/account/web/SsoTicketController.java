package com.hypers.account.web;

import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.AccountUser;
import com.hypers.account.sso.AccountUserSnapshot;
import com.hypers.account.sso.SsoTicketService;
import com.hypers.account.sso.SsoUserPayload;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SsoTicketController {

    private final AccountDirectoryService directoryService;
    private final SsoTicketService ticketService;

    public SsoTicketController(AccountDirectoryService directoryService, SsoTicketService ticketService) {
        this.directoryService = directoryService;
        this.ticketService = ticketService;
    }

    @PostMapping("/api/sso/tickets")
    public IssueTicketResponse issueTicket(@Valid @RequestBody IssueTicketRequest request) {
        if (!directoryService.isAuthorized(request.getUserId(), request.getAppCode())) {
            throw new IllegalArgumentException("user is not authorized for application");
        }
        AccountUser user = directoryService.getUser(request.getUserId());
        AccountApplication application = directoryService.getApplication(request.getAppCode());
        String code = ticketService.issue(application.getAppCode(), new AccountUserSnapshot(
                user.getId(),
                user.getAccount(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                application.getDefaultTenantCode()));
        return new IssueTicketResponse(code);
    }

    @PostMapping("/openapi/sso/tickets/exchange")
    public SsoUserPayload exchange(@Valid @RequestBody ExchangeTicketRequest request) {
        return ticketService.exchange(request.getAppCode(), request.getCode());
    }

    public static class IssueTicketRequest {

        @NotBlank
        private String userId;
        @NotBlank
        private String appCode;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getAppCode() {
            return appCode;
        }

        public void setAppCode(String appCode) {
            this.appCode = appCode;
        }
    }

    public static class IssueTicketResponse {

        private final String code;

        public IssueTicketResponse(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    public static class ExchangeTicketRequest {

        @NotBlank
        private String appCode;
        @NotBlank
        private String code;

        public String getAppCode() {
            return appCode;
        }

        public void setAppCode(String appCode) {
            this.appCode = appCode;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }
}
