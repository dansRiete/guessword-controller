package com.guessword.controller;

import com.guessword.dto.SuggestionDto;
import com.guessword.service.ClaudeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/suggestion")
@RequiredArgsConstructor
public class SuggestionController {

    private final ClaudeService claudeService;

    @GetMapping
    public SuggestionDto getSuggestions(@RequestParam String word, @RequestParam String language) {
        return new SuggestionDto(claudeService.getSuggestions(word, language));
    }
}
