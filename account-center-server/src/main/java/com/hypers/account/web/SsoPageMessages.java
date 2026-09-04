package com.hypers.account.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {AuthController.class, SsoAuthorizeController.class})
@RequiredArgsConstructor
public class SsoPageMessages {
    private final ApiMessages messages;

    @ModelAttribute
    public void pageMessages(HttpServletRequest request, Model model) {
        model.addAttribute("pageLanguage", messages.locale(request).toLanguageTag());
        model.addAttribute("page", messages.fields(request, Map.of(
                "loginTitle", "page.loginTitle", "account", "page.account", "password", "page.password",
                "signIn", "page.signIn", "errorTitle", "page.errorTitle", "authorizationFailed", "page.authorizationFailed",
                "backHome", "page.backHome")));
    }
}
