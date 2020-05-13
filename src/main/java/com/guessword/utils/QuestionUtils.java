package com.guessword.utils;

import com.guessword.entity.Question;

import static com.guessword.utils.ApplicationConstants.*;

public class QuestionUtils {
    public static boolean isStudied(Question question) {
        return question != null && question.getStudyPoints() <= STUDY_COMPLETION_THRESHOLD;
    }
}
