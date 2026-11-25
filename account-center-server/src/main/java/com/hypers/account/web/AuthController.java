package com.hypers.account.web;

import com.hypers.account.app.exception.BusinessException;
import com.hypers.account.auth.AccountLoginService;
import com.hypers.account.auth.AccountSessionUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.ui.Model;
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
    public String loginPage(HttpServletRequest request, Model model) {
        if (request.getParameter("error") != null) {
            model.addAttribute("error", messages.text(request, "error.credentials"));
        }
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
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", messages.text(request, e.getMessageKey()));
            return "redirect:/login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
