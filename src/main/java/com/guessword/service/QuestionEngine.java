package com.guessword.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.guessword.dao.AnswerRepository;
import com.guessword.dto.QuestionDto;
import com.guessword.dto.StatisticsDto;
import com.guessword.dto.mapper.QuestionMapper;
import com.guessword.entity.Answer;
import com.guessword.entity.BaseQuestion;
import com.guessword.entity.Question;
import com.guessword.exception.AnswerException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Contains all the logic for randomizing questions, ensuring the calculation of the probability of asking questions
 * depending on external conditions such as: the number of questions already studied and not studied, the category of
 * currently selected questions and others ...
 *
 * @author Oleksii Kuzko. 9 April 2020.
 */
@Service
public class QuestionEngine {

    public static final double STUDY_COMPLETION_THRESHOLD = 3;
    public static final double STUDY_POINTS_INIT_MULTIPLIER = 1.0;
    public static final double RIGHT_ANSWER_DECREMENT = STUDY_COMPLETION_THRESHOLD;
    public static final double WRONG_ANSWER_INCREMENT = STUDY_COMPLETION_THRESHOLD * 2;
    public static final int STUDIED_QUESTIONS_PROBABILITY = 5;  //  per 100 cases
    public static final int PROBABILITY_ADJUSTING = 100;

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestionEngine.class);

    @Value("${qservice.non-repeatable-questions}")
    public int nonRepeatableQuestions;

    @Value("${qservice.max-retry-attempts}")
    public int questionRetrievingMaxRetriesNumber;

    @Value("${qservice.active-questions}")
    public int activeQuestionsNumber;

    @Value("${qservice.randomizer-factor}")
    private double randomizingFactor;

    @Value("${qservice.index-max-range}")
    private int randomizingIndexesMaxRange;

    @Value("${qservice.learned-margin}")
    private int learnedStudyPointsThreshold;

    @Value("${qservice.index-error}")
    private double allowableIndexErrorFactor;

    @Value("${question-engine.study-points-multiplier}")
    private double questionMultiplier;

    private final AnswerRepository answerRepository;
    private final QuestionMapper questionMapper;
    private final QuestionService questionService;
    private final Map<Integer, QuestionLikelihood> activeNonLearnedQuestions = new HashMap<>();
    private final Map<Integer, QuestionLikelihood> activeLearnedQuestions = new HashMap<>();
    private final List<Answer> answers = new LinkedList<>();

    private Question questionToRollback;

    private long currentMaxIndex;

    public QuestionEngine(
        final AnswerRepository answerRepository, final QuestionMapper questionMapper, final QuestionService questionService) {
        this.questionService = questionService;
        this.answerRepository = answerRepository;
        this.questionMapper = questionMapper;
    }

    public void recalculateRandomizer(double power) {

        if (MapUtils.isEmpty(questionService.getQuestionsCache())) {
            LOGGER.warn("No questions in database. Recalculation was not performed.");
            return;
        }

        long startTime = System.currentTimeMillis();
        activeNonLearnedQuestions.clear();
        double poweredProbFactorSum = 0;
        int learnedIndexes = 0;

        List<Integer> alreadyAnsweredQuestions = answers.stream().limit(nonRepeatableQuestions)
                .map(answer -> answer.getQuestion().getId()).toList();

        List<Question> limitedNonStudiedQuestions = questionService.getQuestionsCache().values().stream()
                .filter(q -> !isStudied(q))
                .filter(q -> !alreadyAnsweredQuestions.contains(q.getId()))
                .sorted(Comparator.comparing(Question::getUuid))     // random, but every time the same order
                .limit(activeQuestionsNumber)
                .toList();

        List<Question> studiedQuestions = questionService.getQuestionsCache().values().stream().filter(this::isStudied).toList();
        List<Question> activeQuestions = Stream.concat(limitedNonStudiedQuestions.stream(), studiedQuestions.stream()).collect(Collectors.toList());
        Collections.shuffle(activeQuestions);

        //  Populating studied, non-studied question map
        for (Question question : activeQuestions) {
            double poweredProbFactor;
            QuestionLikelihood questionLikelihood =
                    new QuestionLikelihood(
                            question.getId(),
                            question.getWords().toString(),
                            question.getStudyPoints(),
                            poweredProbFactor = Math.pow(question.getStudyPoints(), power),
                            -1, -1, -1, -1
                    );
            if (isStudied(question)) {
                activeLearnedQuestions.put(learnedIndexes++, questionLikelihood);
                continue;   // Do not perform calculation for already learned questions
            }
            activeNonLearnedQuestions.put(questionLikelihood.getQuestionId(), questionLikelihood);
            poweredProbFactorSum += poweredProbFactor;
        }

        //  Computing likelihood indexes.
        long index = -1;

        for (QuestionLikelihood question : activeNonLearnedQuestions.values()) {
            if (question.getProbabilityFactor() <= learnedStudyPointsThreshold) {
                continue;
            }
            question.setPercentage(question.getPoweredProbabilityFactor() / poweredProbFactorSum);
            question.setRange(Math.round(randomizingIndexesMaxRange * question.getPercentage()));
            question.setIndexStart(++index);
            question.setIndexEnd(index = index + question.getRange() - 1);
        }

        if (index < randomizingIndexesMaxRange * allowableIndexErrorFactor) {
            LOGGER.warn("Questions' indexes recalculating with fault. End index was: {}, max range {}, "
                + "allowableIndexErrorFactor {}", index, randomizingIndexesMaxRange, allowableIndexErrorFactor);
        }

        LOGGER.debug("Questions' indexes has been recalculated. Non learned questions size: {}. Learned questions size:"
                        + " {}. Total: {}. Time: {} ms. Index range: 0 - {}",
                limitedNonStudiedQuestions.size(),
                activeLearnedQuestions.values().size(),
                activeQuestions.size(),
                System.currentTimeMillis() - startTime,
                index
        );

        currentMaxIndex = index;
    }

    private boolean isStudied(Question question) {
        return question.getStudyPoints() <= learnedStudyPointsThreshold;
    }

    public QuestionDto getRandom() {

        if(activeLearnedQuestions.isEmpty() && activeNonLearnedQuestions.isEmpty()){
            recalculateRandomizer(randomizingFactor);
        }

        QuestionLikelihood selectedQuestion = null;
        int retryAttempts = 0;
        if (RandomUtils.nextInt(0, 100 * PROBABILITY_ADJUSTING + 1) <= STUDIED_QUESTIONS_PROBABILITY * PROBABILITY_ADJUSTING) {
            Object[] questions = activeLearnedQuestions.values().toArray();
            selectedQuestion = (QuestionLikelihood) questions[RandomUtils.nextInt(0, questions.length)];
        } else {
            do {
                retryAttempts++;
                long currentRange = RandomUtils.nextLong(0, currentMaxIndex + 1);
                for (QuestionLikelihood question : activeNonLearnedQuestions.values()) {
                    if (question.isInRange(currentRange)) {
                        /*if (recentlyAnswered(question.getQuestionId())) {
                            continue;
                        }*/
                        selectedQuestion = question;
                    }
                }
            } while (selectedQuestion == null && retryAttempts < questionRetrievingMaxRetriesNumber);
        }

        if (selectedQuestion == null) {
            throw new RuntimeException(String.format("No question was selected, retry attempts: %d", retryAttempts));
        }

        final Question question = questionService.getQuestionsCache().get(selectedQuestion.getQuestionId());

        if (question == null) {
            LOGGER.warn("Could not retrieve next. No question found");
            return null;
        }
        LOGGER.debug(
                String.format(
                        "Retrieved a random question %d %s probFactor = %#.1f",
                        question.getId(),
                        question.getWords(),
                        question.getStudyPoints()
                )
        );
        return questionMapper.toDto(question);
    }

    public QuestionDto answer(BaseQuestion answeredQuestionDto, boolean correct, boolean rollback) {

        Question answeredQuestion;

        if (rollback) {
            answeredQuestion = questionToRollback.toBuilder().build();
            questionService.getQuestionsCache().put(questionToRollback.getId(), questionToRollback.toBuilder().build());
        } else {
            Question questionFromCache = questionService.getQuestionsCache().get(answeredQuestionDto.getId());
            if (questionFromCache == null) {
                throw new AnswerException(String.format("Couldn't answer the question id: <%d>", answeredQuestionDto.getId()));
            }
            answeredQuestion = questionFromCache.toBuilder().build();
            questionToRollback = answeredQuestion.toBuilder().build();
        }

        Double studyPointsBefore = answeredQuestion.getStudyPoints();
        Question updatedQuestion;

        if (correct) {
            updatedQuestion = rightAnswer(answeredQuestion, rollback);
        } else {
            updatedQuestion = wrongAnswer(answeredQuestion, rollback);
        }
        Double studyPointsAfter = updatedQuestion.getStudyPoints();

        if (rollback && !answers.isEmpty() && answers.get(0).getQuestion() != null
                && updatedQuestion.getId().equals(answers.get(0).getQuestion().getId())) {
            Answer previousAnswer = answers.get(0);
            previousAnswer.setAnswered(ZonedDateTime.now(ZoneId.of("UTC")));
            previousAnswer.setQuestion(updatedQuestion);
            previousAnswer.setCorrect(correct);
            previousAnswer.setStudyPointsBefore(studyPointsBefore);
            previousAnswer.setStudyPointsAfter(studyPointsAfter);
            answerRepository.saveAndFlush(previousAnswer);
        } else {
            Answer answer = Answer.builder()
                    .answered(ZonedDateTime.now(ZoneId.of("UTC")))
                    .question(updatedQuestion)
                    .correct(correct)
                    .studyPointsBefore(studyPointsBefore)
                    .studyPointsAfter(studyPointsAfter)
                    .build();
            answer = answerRepository.saveAndFlush(answer);
            answers.add(0, answer);
        }

        return questionMapper.toDto(updatedQuestion);
    }

    public StatisticsDto getTodayStatistics(String tz) {
        ZoneId zoneId;
        try {
            zoneId = (tz == null || tz.isBlank()) ? ZoneId.of("America/New_York") : ZoneId.of(tz);
        } catch (Exception e) {
            zoneId = ZoneId.of("America/New_York");
        }
        ZonedDateTime startOfTodayUtc = LocalDate.now(zoneId).atStartOfDay(zoneId).withZoneSameInstant(ZoneId.of("UTC"));
        List<Answer> todayAnswers = answerRepository.findByAnsweredGreaterThanEqualOrderByAnsweredAsc(startOfTodayUtc);

        int total = todayAnswers.size();
        int rights = 0;
        int studied = 0;
        Set<Integer> legacyStudiedQuestionIds = new HashSet<>();

        for (Answer answer : todayAnswers) {
            if (Boolean.TRUE.equals(answer.getCorrect())) {
                rights++;
            }
            Double before = answer.getStudyPointsBefore();
            Double after = answer.getStudyPointsAfter();
            if (before != null && after != null) {
                if (before > STUDY_COMPLETION_THRESHOLD && after <= STUDY_COMPLETION_THRESHOLD) {
                    studied++;
                } else if (before <= STUDY_COMPLETION_THRESHOLD && after > STUDY_COMPLETION_THRESHOLD) {
                    studied--;
                }
            } else if (Boolean.TRUE.equals(answer.getCorrect())
                    && answer.getQuestion() != null
                    && answer.getQuestion().getStudyPoints() != null
                    && answer.getQuestion().getStudyPoints() <= STUDY_COMPLETION_THRESHOLD) {
                legacyStudiedQuestionIds.add(answer.getQuestion().getId());
            }
        }
        studied += legacyStudiedQuestionIds.size();
        int rightPercentage = total == 0 ? 0 : (rights * 100 / total);
        return new StatisticsDto(total, studied, rightPercentage);
    }

    public void saveActualAnswer(Integer questionId, String actualAnswer) {
        Answer targetAnswer = answers.stream()
                .filter(a -> a.getQuestion() != null && questionId.equals(a.getQuestion().getId()))
                .findFirst()
                .orElseGet(() -> answerRepository.findFirstByQuestionIdOrderByAnsweredDesc(questionId)
                        .orElseThrow(() -> new AnswerException(
                                String.format("No answer found for question id: <%d>", questionId))));
        targetAnswer.setActualAnswer(actualAnswer);
        answerRepository.saveAndFlush(targetAnswer);
        LOGGER.info("Saved actual answer '{}' for question id {}", actualAnswer, questionId);
    }

    private Question rightAnswer(Question answeredQuestion, boolean rollback) {

        Double previousStudyPoints = answeredQuestion.getStudyPoints();
        Double previousStudyPointsMultiplier = answeredQuestion.getStudyPointsMultiplier();
        if (!isStudied(answeredQuestion)) {
            answeredQuestion.setStudyPoints(previousStudyPoints - RIGHT_ANSWER_DECREMENT * previousStudyPointsMultiplier);
            answeredQuestion.setStudyPointsMultiplier(
                    previousStudyPointsMultiplier == STUDY_POINTS_INIT_MULTIPLIER ? questionMultiplier :
                            previousStudyPointsMultiplier * previousStudyPointsMultiplier
            );
        }

        LOGGER.info(String.format(
                "%sRight answered the question %d %s probFactor: [%#.1f => %#.1f], probMultiplier: [%#.2f => %#.2f]",
                rollback ? "Rollbacked with " : "", answeredQuestion.getId(), answeredQuestion.getWords(),
                previousStudyPoints, answeredQuestion.getStudyPoints(),
                previousStudyPointsMultiplier, answeredQuestion.getStudyPointsMultiplier()
        ));

        answeredQuestion.setLastAccessed(ZonedDateTime.now(ZoneId.of("UTC"))); // todo should we move setting lastAccessed to getRandom method?
        Question updatedQuestion = questionService.updateQuestion(answeredQuestion);

        recalculateRandomizer(randomizingFactor);

        return updatedQuestion;
    }

    private Question wrongAnswer(Question answeredQuestion, boolean rollback) {

        Double previousStudyPoints = answeredQuestion.getStudyPoints();
        Double previousStudyPointsMultiplier = answeredQuestion.getStudyPointsMultiplier();
        if (isStudied(answeredQuestion)) {
            answeredQuestion.setStudyPoints(
                    previousStudyPoints + WRONG_ANSWER_INCREMENT * answeredQuestion.getStudyPointsMultiplier());
        } else {
            answeredQuestion.setStudyPoints(previousStudyPoints + WRONG_ANSWER_INCREMENT);
        }
        answeredQuestion.setStudyPointsMultiplier(STUDY_POINTS_INIT_MULTIPLIER);

        LOGGER.info(
                String.format(
                        "%sWrong answer the question %d %s probFactor: [%#.1f => %#.1f], probMultiplier: [%#.2f => %#.2f]",
                        rollback ? "Rollbacked with " : "", answeredQuestion.getId(), answeredQuestion.getWords(),
                        previousStudyPoints, answeredQuestion.getStudyPoints(), previousStudyPointsMultiplier,
                        answeredQuestion.getStudyPointsMultiplier()
                ));

        answeredQuestion.setLastAccessed(ZonedDateTime.now());
        Question updatedQuestion = questionService.updateQuestion(answeredQuestion);
        recalculateRandomizer(randomizingFactor);
        return updatedQuestion;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class QuestionLikelihood {
        private int questionId;
        private String words;
        private double probabilityFactor;
        private double poweredProbabilityFactor;
        private double percentage;
        private long range;
        private long indexStart;
        private long indexEnd;

        public boolean isInRange(long range) {
            return range >= indexStart && range <= indexEnd;
        }

        @Override
        public String toString() {
            return String.format("QuestionLikelihood{questionId=%d, words='%s'," +
                    " studyPoints=%.1f, indexStart=%d, indexEnd=%d}",
                    questionId, words, probabilityFactor, indexStart, indexEnd
            );
        }
    }
}
