package com.guessword.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"id", "language", "transcription"}, includeFieldNames = false)
public class WordDto {
    private Integer id;
    private String word;
    private String language;
    private String transcription;
}
