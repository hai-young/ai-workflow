package com.zhy.workflow.ai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Page controller for serving static HTML pages.
 */
@Controller
public class PageController {

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    @ResponseBody
    public String loginPage() {
        try {
            return new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("src/main/resources/templates/login.html")
            ));
        } catch (Exception e) {
            return "<html><head><style>body{font-family:system-ui;margin:0;padding:40px;text-align:center;}</style></head><body><h1>404 - 页面未找到</h1><p>" + e.getMessage() + "</p></body></html>";
        }
    }

    @GetMapping("/register")
    @ResponseBody
    public String registerPage() {
        try {
            return new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("src/main/resources/templates/register.html")
            ));
        } catch (Exception e) {
            return "<html><head><style>body{font-family:system-ui;margin:0;padding:40px;text-align:center;}</style></head><body><h1>404 - 页面未找到</h1><p>" + e.getMessage() + "</p></body></html>";
        }
    }
}
