package com.guessword.service;

import com.guessword.dao.AnswerRepository;
import com.guessword.dao.QuestionDao;
import com.guessword.dao.QuestionRepository;
import com.guessword.entity.Answer;
import com.guessword.entity.BaseQuestion;
import com.guessword.dto.QuestionDto;
import com.guessword.dto.mapper.QuestionMapper;
import com.guessword.entity.Question;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.guessword.utils.ApplicationConstants.*;
import static com.guessword.utils.QuestionUtils.isStudied;

/**
 * Contains all the logic for randomizing questions, ensuring the calculation of the probability of asking questions
 * depending on external conditions such as: the number of questions already studied and not studied, the category of
 * currently selected questions and others ...
 * @author Oleksii Kuzko. 9 April 2020.
 */
@Service
public class QuestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestionService.class);
    private static final double CHANCE_OF_APPEARING_TRAINED_PHRASES = 1D / 35D;
    private static final double POWER = 1.25;
    private static final int MAX_RANGE = 1_000_000;
    private static final int LEARNED_QUESTION_PROB_MARGIN = 3;
    private static final double ALLOWABLE_INDEX_ERROR_FACTOR = 0.95;

    private final QuestionDao questionDao;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionMapper questionMapper;

    private final Map<Integer, QuestionLikelihood> nonLearnedQuestions = new HashMap<>();
    private final Map<Integer, QuestionLikelihood> learnedQuestions = new HashMap<>();
    private final Map<Integer, Answer> answers = new HashMap<>();

    private Map<Integer, Question> questionMap;
    private Question questionToRollback;
    private Answer answerToRollback;
    private long currentMaxIndex;

    public QuestionService(
            final QuestionDao questionDao,
            final QuestionRepository questionRepository,
            AnswerRepository answerRepository, final QuestionMapper questionMapper
    ) {
        this.questionDao = questionDao;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.questionMapper = questionMapper;
    }

    public List<QuestionDto> findAllMysql() {
        long startTime = System.currentTimeMillis();
        final List<Question> allMysqlQuestions = questionDao.getAllMysql();
        LOGGER.info("Fetched all from MySQL. Time: {} ms. Found: {}",
            System.currentTimeMillis() - startTime, allMysqlQuestions.size());
        return questionMapper.toDto(allMysqlQuestions);
    }

    @Transactional
    public boolean copyAll() {
        long startTime = System.currentTimeMillis();
        List<Question> questions = questionDao.getAllMysql();
        questionRepository.saveAll(questions);
        LOGGER.info("Copied all question from MySQL. Time: {} ms. Size: {}",
                System.currentTimeMillis() - startTime, questions.size());
        return true;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        long startTime = System.currentTimeMillis();
        questionMap = questionRepository.findAll().stream().collect(
            Collectors.toMap(Question::getId, Function.identity())
        );
        recalculateRandomizer(POWER);
        LOGGER.info(String.format("QuestionEngine has been initialized. Questions size: %d - Time: %d ms.",
            questionMap.values().size(), System.currentTimeMillis() - startTime)
        );
    }

    public List<Map<Integer, QuestionLikelihood>> recalculateRandomizer(double power) {

        if (MapUtils.isEmpty(questionMap)) {
            LOGGER.warn("No questions in database. Recalculation was not performed.");
            return null;
        }

        long startTime = System.currentTimeMillis();

        nonLearnedQuestions.clear();
        double poweredProbFactorSum = 0;
        int learnedIndexes = 0;

        for (Question question : questionMap.values()) {
            double poweredProbFactor;
            QuestionLikelihood questionLikelihood =
                new QuestionLikelihood(
                    question.getId(),
                    question.getStudyPoints(),
                    poweredProbFactor = Math.pow(question.getStudyPoints(), power),
                    -1,
                    -1,
                    -1,
                    -1
                );
            if (question.getStudyPoints() <= LEARNED_QUESTION_PROB_MARGIN) {
                learnedQuestions.put(learnedIndexes++, questionLikelihood);
                continue;   // Do not perform calculation for already learned questions
            }
            nonLearnedQuestions.put(questionLikelihood.getId(), questionLikelihood);
            poweredProbFactorSum += poweredProbFactor;
        }

        long index = -1;
        for (QuestionLikelihood question : nonLearnedQuestions.values()) {
            if (question.getProbabilityFactor() <= LEARNED_QUESTION_PROB_MARGIN) {
                continue;
            }
            question.setPercentage(question.getPoweredProbabilityFactor() / poweredProbFactorSum);
            question.setRange(Math.round(MAX_RANGE * question.getPercentage()));
            question.setIndexStart(++index);
            question.setIndexEnd(index = index + question.getRange() - 1);
        }
        if (index < MAX_RANGE * ALLOWABLE_INDEX_ERROR_FACTOR) {
            LOGGER.warn("Questions' indexes recalculating with fault. End index was: {}", index);
        }
        LOGGER.debug("Questions' indexes has been recalculated. Non learned questions size: {}. Learned questions size:"
                + " {}. Time: {} ms. Index end: {}",
            questionMap.values().size(),
            learnedQuestions.values().size(),
            System.currentTimeMillis() - startTime,
            index
        );
        currentMaxIndex = index;
        return Arrays.asList(nonLearnedQuestions, learnedQuestions);
    }

    public QuestionDto getRandom() {

        long currentRange = RandomUtils.nextLong(0, currentMaxIndex + 1);
        QuestionLikelihood selectedQuestion = null;

        for(QuestionLikelihood question : nonLearnedQuestions.values()) {
            if (question.isInRange(currentRange)){
                selectedQuestion = question;
            }
        }

        if(selectedQuestion == null) {
            throw new RuntimeException("No question was selected");
        }

        final Question question = questionMap.get(selectedQuestion.getId());

        if(question == null) {
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
            questionMap.put(questionToRollback.getId(), questionToRollback.toBuilder().build());
        } else {
            answeredQuestion = questionMap.get(answeredQuestionDto.getId()).toBuilder().build();
            questionToRollback = answeredQuestion.toBuilder().build();
        }

        Question updatedQuestion;

        if (correct) {
            updatedQuestion = rightAnswer(answeredQuestion, rollback);
        } else {
            updatedQuestion = wrongAnswer(answeredQuestion, rollback);
        }

        Answer answer = Answer.builder().answered(LocalDateTime.now())
                .question(updatedQuestion).correct(correct).build();
        answer = answerRepository.saveAndFlush(answer);
        answers.put(answer.getId(), answer);

        return questionMapper.toDto(updatedQuestion);
    }

    private Question rightAnswer(Question answeredQuestion, boolean rollback) {

        Double previousStudyPoints = answeredQuestion.getStudyPoints();
        Double previousStudyPointsMultiplier = answeredQuestion.getStudyPointsMultiplier();
        answeredQuestion.setStudyPoints(previousStudyPoints - RIGHT_ANSWER_DECREMENT * previousStudyPointsMultiplier);
        answeredQuestion.setStudyPointsMultiplier(
                previousStudyPointsMultiplier == STUDY_POINTS_INIT_MULTIPLIER ? STUDY_POINTS_MULTIPLIER :
                        previousStudyPointsMultiplier * STUDY_POINTS_MULTIPLIER
        );

        LOGGER.info(String.format(
                        "%sRight answered the question %d %s probFactor: [%#.1f => %#.1f], probMultiplier: [%#.2f => %#.2f]",
                        rollback ? "Rollbacked with " : "", answeredQuestion.getId(), answeredQuestion.getWords(),
                        previousStudyPoints, answeredQuestion.getStudyPoints(),
                        previousStudyPointsMultiplier, answeredQuestion.getStudyPointsMultiplier()
                ));

        answeredQuestion.setLastAccessed(LocalDateTime.now());
        Question updatedQuestion = questionRepository.saveAndFlush(answeredQuestion);
        questionMap.put(updatedQuestion.getId(), updatedQuestion);
        recalculateRandomizer(POWER);
        return updatedQuestion;
    }

    private Question wrongAnswer(Question answeredQuestion, boolean rollback) {

        Double previousStudyPoints = answeredQuestion.getStudyPoints();
        Double previousStudyPointsMultiplier = answeredQuestion.getStudyPointsMultiplier();
        if(isStudied(answeredQuestion)) {
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

        answeredQuestion.setLastAccessed(LocalDateTime.now());
        Question updatedQuestion = questionRepository.saveAndFlush(answeredQuestion);
        questionMap.put(updatedQuestion.getId(), updatedQuestion);
        recalculateRandomizer(POWER);
        return updatedQuestion;
    }

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    public static class QuestionLikelihood {
        int id;
        double probabilityFactor;
        double poweredProbabilityFactor;
        double percentage;
        long range;
        long indexStart = -1;
        long indexEnd = -1;

        public boolean isInRange(long range) {
            return range >= indexStart && range <= indexEnd;
        }
    }
}
