package com.guessword.dto;

import java.time.ZonedDateTime;
import java.util.List;

import com.guessword.entity.BaseQuestion;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"created", "lastAccessed", "tag"}, includeFieldNames = false)
public class QuestionDto implements BaseQuestion {
    private Integer id;
    private Double studyPoints;
    private Double studyPointsMultiplier;
    private ZonedDateTime created;
    private ZonedDateTime lastAccessed;
    private String tag;
    private List<WordDto> words;
}
