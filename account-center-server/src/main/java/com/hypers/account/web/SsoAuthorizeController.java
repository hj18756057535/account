package com.hypers.account.web;

import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.AccountUser;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.sso.AccountUserSnapshot;
import com.hypers.account.sso.SsoTicketService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import lombok.RequiredArgsConstructor;

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
@RequiredArgsConstructor
public class SsoAuthorizeController {

    private final AccountDirectoryService directoryService;
    private final SsoTicketService ticketService;

    private final ApiMessages messages;

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
            return "redirect:/sso/error?code=applicationNotFound";
        }
        if ("disabled".equals(application.getStatus())) {
            return "redirect:/sso/error?code=applicationDisabled";
        }

        // 检查用户是否授权该应用
        if (!directoryService.isAuthorized(sessionUser.getUserId(), appCode)) {
            return "redirect:/sso/error?code=accessDenied";
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
    public String errorPage(@RequestParam(required = false) String code,
                            @RequestParam(required = false) String message,
                            HttpServletRequest request, Model model) {
        // Only known reasons are rendered; legacy message links remain readable without reflecting arbitrary text.
        String key = switch (code != null ? code : String.valueOf(message)) {
            case "applicationNotFound", "应用不存在" -> "sso.applicationNotFound";
            case "applicationDisabled", "应用已禁用" -> "sso.applicationDisabled";
            case "accessDenied", "您没有该应用的访问权限" -> "sso.accessDenied";
            case "callbackMismatch", "回调地址不匹配" -> "sso.callbackMismatch";
            default -> "sso.unknown";
        };
        model.addAttribute("message", messages.text(request, key));
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
