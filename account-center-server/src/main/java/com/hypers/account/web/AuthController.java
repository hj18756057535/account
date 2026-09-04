package com.hypers.account.web;

import com.hypers.account.auth.AccountLoginService;
import com.hypers.account.auth.AccountSessionUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Account Center 登录/登出控制器。
 * 使用 HttpSession 管理登录状态。
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    public static final String SESSION_USER_KEY = "accountSessionUser";

    private final AccountLoginService loginService;

    private final ApiMessages messages;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String account,
                          @RequestParam String password,
                          HttpSession session,
                          HttpServletRequest request,
                          RedirectAttributes redirectAttributes) {
        try {
            AccountSessionUser user = loginService.authenticate(account, password);
            session.setAttribute(SESSION_USER_KEY, user);
            // 登录成功后跳转到之前请求的页面或首页
            String redirectUrl = (String) session.getAttribute("loginRedirectUrl");
            if (redirectUrl != null) {
                session.removeAttribute("loginRedirectUrl");
                return "redirect:" + redirectUrl;
            }
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            String key = switch (String.valueOf(e.getMessage())) {
                case "账号已禁用" -> "error.accountDisabled";
                case "该账号未设置密码，请联系管理员" -> "error.passwordNotSet";
                default -> "error.credentials";
            };
            redirectAttributes.addFlashAttribute("error", messages.text(request, key));
            return "redirect:/login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
