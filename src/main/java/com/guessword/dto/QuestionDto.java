package com.guessword.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.guessword.entity.BaseQuestion;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"created", "lastAccessed", "tag"}, includeFieldNames = false)
public class QuestionDto implements BaseQuestion {
    private Integer id;
    private Double studyPoints;
    private Double studyPointsMultiplier;
    private LocalDateTime created;
    private LocalDateTime lastAccessed;
    private String tag;
    private List<WordDto> words;
}
