package com.guessword.utils;

public class ApplicationConstants {

    public static final double STUDY_COMPLETION_THRESHOLD = 3;
    public static final double STUDY_POINTS_INIT_MULTIPLIER = 1.0;
    public static final double RIGHT_ANSWER_DECREMENT = STUDY_COMPLETION_THRESHOLD;
    public static final double WRONG_ANSWER_INCREMENT = STUDY_COMPLETION_THRESHOLD * 2;
    public static final int STUDIED_QUESTIONS_PROBABILITY = 5; // per 100 cases
    public static final int PROBABILITY_ADJUSTING = 100;
    public static final int PROBABILITY_OF_ALREADY_ANSWERED = 3;

}
