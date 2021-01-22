package com.guessword.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/test")
public class ThymeleafController {


    @GetMapping("/hello")
    public String index(Model model) {
        model.addAttribute("message", "HELLO!");
        return "hello";
    }

}
