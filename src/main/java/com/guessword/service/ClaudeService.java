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

    public String checkTranslation(String en, String ru, String comment) {
        if (apiKey.isBlank()) return "Claude API key is not configured.";
        try {
            String cleanEn = en == null ? "" : en.trim();
            String cleanRu = ru == null ? "" : ru.trim();
            String cleanComment = comment == null ? "" : comment.trim();
            String commentSection = cleanComment.isEmpty() ? "" :
                    "Author's comment / desired meaning or context: \"" + cleanComment + "\"\n" +
                    "Pay close attention to the author's comment to understand what exact meaning, nuance, or phrase they want to learn.\n";
            String collocationInstruction =
                    "If the word or phrase is ambiguous, polysemous, or too generic on its own " +
                    "(for example, a standalone word like \"joint\" or \"совместный\"), suggest a short, " +
                    "natural collocation with context directly (e.g., \"joint effort\" and \"совместные усилия\") " +
                    "so it is unambiguous and effective to learn. Avoid repeating the word in parentheses " +
                    "like \"joint (joint effort)\" — keep the suggested phrase clean and distraction-free.\n";
            String otherWaysInstruction =
                    "Always include a section titled \"**Other ways to say it:**\" with 2-4 natural English " +
                    "alternatives or synonyms.\n";
            String prompt;
            if (cleanEn.isEmpty() && cleanRu.isEmpty()) {
                prompt = "You are helping with an English-Russian vocabulary learning app.\n" +
                        commentSection +
                        "Based on the author's comment, provide a short explanation (maximum 3-4 bullet points) " +
                        "and recommend the best Russian-English phrase pair.\n" +
                        otherWaysInstruction +
                        collocationInstruction +
                        "At the very end, state your recommended pair using these exact two lines:\n" +
                        "**Suggested RU:** <suggested Russian phrase>\n" +
                        "**Suggested EN:** <suggested English phrase>";
            } else if (cleanEn.isEmpty() || cleanRu.isEmpty()) {
                String sourceLang = cleanEn.isEmpty() ? "Russian" : "English";
                String targetLang = cleanEn.isEmpty() ? "English" : "Russian";
                String sourcePhrase = cleanEn.isEmpty() ? cleanRu : cleanEn;
                prompt = "You are helping with an English-Russian vocabulary learning app.\n" +
                        "The user provided the " + sourceLang + " phrase: \"" + sourcePhrase + "\" " +
                        "and left the " + targetLang + " translation empty.\n" +
                        commentSection +
                        "Provide a short, concise explanation (maximum 3-4 bullet points) of the best " +
                        targetLang + " translations and their nuances.\n" +
                        otherWaysInstruction +
                        collocationInstruction +
                        "At the very end, state your recommended pair using these exact two lines:\n" +
                        "**Suggested RU:** <suggested Russian phrase>\n" +
                        "**Suggested EN:** <suggested English phrase>";
            } else {
                prompt = "You are helping with an English-Russian vocabulary learning app.\n" +
                        "English phrase: \"" + cleanEn + "\"\n" +
                        "Russian phrase: \"" + cleanRu + "\"\n" +
                        commentSection +
                        "Check the correctness of the English translation for the Russian phrase. " +
                        "Provide a short, concise summary (maximum 4-5 sentences or bullet points) " +
                        "about whether it is correct, any nuance differences, or better alternatives if needed.\n" +
                        otherWaysInstruction +
                        "If the translation does not seem completely accurate, natural, or complete, or if the word " +
                        "is ambiguous on its own, or if the author's comment requests a specific adjustment, " +
                        "do your best to guess what the author tried to learn by this phrase " +
                        "and come up with the best corrected pair for BOTH Russian and English.\n" +
                        collocationInstruction +
                        "State your suggestion clearly at the very end using these exact two lines:\n" +
                        "**Suggested RU:** <suggested Russian phrase>\n" +
                        "**Suggested EN:** <suggested English phrase>\n" +
                        "If the existing pair is already completely accurate, unambiguous, and needs no changes, omit those two lines.";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "max_tokens", 500,
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
