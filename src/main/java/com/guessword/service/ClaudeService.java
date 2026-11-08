package com.guessword.service;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ClaudeService {

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();

    @Value("${claude.api-key:}")
    private String apiKey;

    public List<String> getSuggestions(String word, String language) {
        if (apiKey.isBlank()) return List.of();
        return cache.computeIfAbsent(language + ":" + word.toLowerCase().trim(), k -> fetchFromClaude(word, language));
    }

    public String checkTranslation(String en, String ru) {
        if (apiKey.isBlank()) return "Claude API key is not configured.";
        try {
            String prompt = "You are helping with an English-Russian vocabulary learning app.\n" +
                    "English phrase: \"" + en + "\"\n" +
                    "Russian phrase: \"" + ru + "\"\n" +
                    "Check the correctness of the English translation for the Russian phrase. " +
                    "Provide a short, concise summary (maximum 4-5 sentences or bullet points) " +
                    "about whether it is correct, any nuance differences, or better alternatives if needed.";

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "max_tokens", 200,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            ClaudeResponse response = restTemplate.postForObject(
                    CLAUDE_API_URL, new HttpEntity<>(body, headers), ClaudeResponse.class);

            if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
                return "No response from LLM.";
            }
            return response.getContent().get(0).getText().trim();
        } catch (Exception e) {
            return "Error checking translation: " + e.getMessage();
        }
    }

    private List<String> fetchFromClaude(String word, String language) {
        try {
            String targetLang = "en".equals(language) ? "Russian" : "English";
            String sourceLang = "en".equals(language) ? "English" : "Russian";
            String prompt = "You are helping build an English-Russian vocabulary learning app. " +
                    "The user typed the " + sourceLang + " word/phrase: \"" + word + "\". " +
                    "List up to 5 " + targetLang + " translations, one per line. " +
                    "Return only the " + targetLang + " words/phrases, nothing else.";

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "max_tokens", 150,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            ClaudeResponse response = restTemplate.postForObject(
                    CLAUDE_API_URL, new HttpEntity<>(body, headers), ClaudeResponse.class);

            if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
                return List.of();
            }
            return Arrays.stream(response.getContent().get(0).getText().split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    @Data
    private static class ClaudeResponse {
        private List<ContentBlock> content;

        @Data
        private static class ContentBlock {
            private String type;
            private String text;
        }
    }
}
