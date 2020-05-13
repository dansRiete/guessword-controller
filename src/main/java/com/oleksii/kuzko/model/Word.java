package com.oleksii.kuzko.model;

import com.google.common.base.MoreObjects;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author The Weather Company, An IBM Business
 */
public class Word extends BaseModel{

    private final String word;
    private final String language;
    private final String transcription;
    private final LocalDateTime additionDate;

    public Word(String id, String word, String language, String transcription,
            LocalDateTime additionDate) {
        super(id);
        this.word = normalizeWord(word);
        this.language = language;
        this.transcription = transcription;
        this.additionDate = additionDate;
    }

    public String getWord() {
        return word;
    }

    public String getLanguage() {
        return language;
    }

    public String getTranscription() {
        return transcription;
    }

    private String normalizeWord(String word) {
        return Arrays.stream(word.split(" "))
                .map(word1 -> word1.equals("I") ? "I" : word1.toLowerCase()).collect(Collectors.joining(" "));
    }

    @Override
    public int hashCode() {
        return Objects.hash(word, language, transcription);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Word word1 = (Word) o;
        return Objects.equals(word, word1.word) &&
                Objects.equals(language, word1.language) &&
                Objects.equals(transcription, word1.transcription);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("word", word)
                .add("language", language)
                .add("transcription", transcription)
                .toString();
    }

    public LocalDateTime getAdditionDate() {
        return additionDate;
    }
}
