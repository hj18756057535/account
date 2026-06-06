package com.hypers.account.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/users")
    public String users() {
        return "users";
    }

    @GetMapping("/applications")
    public String applications() {
        return "applications";
    }
}
