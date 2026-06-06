package com.hypers.account.web;

import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.AccountUser;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.sso.AccountUserSnapshot;
import com.hypers.account.sso.SsoTicketService;
import javax.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * SSO 授权端点控制器。
 * 实现标准 SSO 浏览器流程：应用跳转 -> 检查登录 -> 检查授权 -> 生成 code -> 回调应用。
 *
 * 流程：
 * 1. 用户访问应用，应用发现未登录，跳转到 /sso/authorize
 * 2. Account Center 检查自身登录态，未登录则跳转 /login 并保存回调地址
 * 3. 已登录则检查用户是否授权该应用
 * 4. 已授权则生成一次性 code 并重定向回应用 callback
 * 5. 未授权则显示无权限页面
 */
@Controller
public class SsoAuthorizeController {

    private final AccountDirectoryService directoryService;
    private final SsoTicketService ticketService;

    public SsoAuthorizeController(AccountDirectoryService directoryService, SsoTicketService ticketService) {
        this.directoryService = directoryService;
        this.ticketService = ticketService;
    }

    @GetMapping("/sso/authorize")
    public String authorize(@RequestParam String appCode,
                            @RequestParam String redirectUri,
                            @RequestParam(required = false) String state,
                            HttpSession session) {
        // 检查 Account Center 登录态
        AccountSessionUser sessionUser = (AccountSessionUser) session.getAttribute(AuthController.SESSION_USER_KEY);
        if (sessionUser == null) {
            // 未登录，保存 SSO 请求参数到 session，登录后继续
            session.setAttribute("loginRedirectUrl",
                    buildAuthorizeUrl(appCode, redirectUri, state));
            return "redirect:/login";
        }

        // 检查应用是否存在且启用
        AccountApplication application;
        try {
            application = directoryService.getApplication(appCode);
        } catch (IllegalArgumentException e) {
            return "redirect:/sso/error?message=" + encode("应用不存在");
        }
        if ("disabled".equals(application.getStatus())) {
            return "redirect:/sso/error?message=" + encode("应用已禁用");
        }

        // 检查用户是否授权该应用
        if (!directoryService.isAuthorized(sessionUser.getUserId(), appCode)) {
            return "redirect:/sso/error?message=" + encode("您没有该应用的访问权限");
        }

        // 生成一次性 SSO code
        AccountUser user = directoryService.getUser(sessionUser.getUserId());
        String code = ticketService.issue(appCode, new AccountUserSnapshot(
                user.getId(),
                user.getAccount(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                application.getDefaultTenantCode()));

        // 重定向回应用 callback
        StringBuilder callbackUrl = new StringBuilder(redirectUri);
        callbackUrl.append(redirectUri.contains("?") ? "&" : "?");
        callbackUrl.append("code=").append(code);
        if (state != null && !state.isEmpty()) {
            callbackUrl.append("&state=").append(encode(state));
        }
        return "redirect:" + callbackUrl.toString();
    }

    @GetMapping("/sso/error")
    public String errorPage() {
        return "sso-error";
    }

    private String buildAuthorizeUrl(String appCode, String redirectUri, String state) {
        StringBuilder url = new StringBuilder("/sso/authorize?");
        url.append("appCode=").append(encode(appCode));
        url.append("&redirectUri=").append(encode(redirectUri));
        if (state != null && !state.isEmpty()) {
            url.append("&state=").append(encode(state));
        }
        return url.toString();
    }

    private String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
}
