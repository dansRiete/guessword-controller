package com.guessword.controller;

import com.guessword.dto.SuggestionDto;
import com.guessword.service.ClaudeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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

    @GetMapping(value = "/check", produces = MediaType.TEXT_PLAIN_VALUE)
    public String checkTranslation(@RequestParam(defaultValue = "") String en,
                                   @RequestParam(defaultValue = "") String ru,
                                   @RequestParam(defaultValue = "") String comment) {
        return claudeService.checkTranslation(en, ru, comment);
    }
}
