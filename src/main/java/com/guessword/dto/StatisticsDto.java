package com.guessword.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsDto {
    private int answeredToday;
    private int studiedToday;
    private int rightAnswerPercentage;
}
