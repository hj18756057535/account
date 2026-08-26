package com.hypers.account.web;

import com.hypers.account.admin.AdminTicketPayload;
import com.hypers.account.admin.AdminTicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 ticket 控制器。
 * 提供 ticket 签发和验证能力，用于 iframe 授权页面的一次性身份凭证。
 */
@RestController
public class AdminTicketController {

    private final AdminTicketService ticketService;

    public AdminTicketController(AdminTicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** 签发 admin ticket（60 秒有效期，一次性使用） */
    @PostMapping("/api/admin-tickets")
    public IssueAdminTicketResponse issue(@Valid @RequestBody IssueAdminTicketRequest request) {
        String code = ticketService.issue(request.getAppCode(), request.getUserId(), request.getPurpose());
        return new IssueAdminTicketResponse(code);
    }

    /** 验证 admin ticket（校验有效性和一次性使用） */
    @PostMapping("/openapi/admin-tickets/verify")
    public AdminTicketPayload verify(@Valid @RequestBody VerifyAdminTicketRequest request) {
        return ticketService.verify(request.getAppCode(), request.getTicket());
    }

    public static class IssueAdminTicketRequest {

        @NotBlank
        private String appCode;
        @NotBlank
        private String userId;
        private String purpose;

        public IssueAdminTicketRequest() {
        }

        public String getAppCode() { return appCode; }
        public void setAppCode(String appCode) { this.appCode = appCode; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getPurpose() { return purpose; }
        public void setPurpose(String purpose) { this.purpose = purpose; }
    }

    public static class IssueAdminTicketResponse {

        private final String code;

        public IssueAdminTicketResponse(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    public static class VerifyAdminTicketRequest {

        @NotBlank
        private String appCode;
        @NotBlank
        private String ticket;

        public VerifyAdminTicketRequest() {
        }

        public String getAppCode() { return appCode; }
        public void setAppCode(String appCode) { this.appCode = appCode; }
        public String getTicket() { return ticket; }
        public void setTicket(String ticket) { this.ticket = ticket; }
    }
}
