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
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.guessword.utils.ApplicationConstants.*;
import static com.guessword.utils.QuestionUtils.isStudied;

/**
 * Contains all the logic for randomizing questions, ensuring the calculation of the probability of asking questions
 * depending on external conditions such as: the number of questions already studied and not studied, the category of
 * currently selected questions and others ...
 *
 * @author Oleksii Kuzko. 9 April 2020.
 */
@Service
public class QuestionService {

    public static final int MAX_RETRY_ATTEMPTS = 100;
    public static final int LIMITED_QUESTION_NUMBER = 300;
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestionService.class);
    private static final double POWER = 1.75;
    private static int MAX_RANGE = 1_000_000;
    private static final int LEARNED_QUESTION_PROB_MARGIN = 3;
    private static final double ALLOWABLE_INDEX_ERROR_FACTOR = 0.95;

    private final QuestionDao questionDao;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionMapper questionMapper;

    private final Map<Integer, QuestionLikelihood> nonLearnedQuestions = new HashMap<>();
    private final Map<Integer, QuestionLikelihood> learnedQuestions = new HashMap<>();
    private final List<Answer> answers = new LinkedList<>();

    private Map<Integer, Question> allQuestionMap;
    private Question questionToRollback;
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
        allQuestionMap = questionRepository.findAll().stream().collect(
                Collectors.toMap(Question::getId, Function.identity())
        );
        MAX_RANGE = allQuestionMap.values().size() * PROBABILITY_ADJUSTING;
        recalculateRandomizer(POWER);
        LOGGER.info(String.format("QuestionEngine has been initialized. Questions size: %d - Time: %d ms.",
                allQuestionMap.values().size(), System.currentTimeMillis() - startTime)
        );
    }

    public List<Map<Integer, QuestionLikelihood>> recalculateRandomizer(double power) {

        if (MapUtils.isEmpty(allQuestionMap)) {
            LOGGER.warn("No questions in database. Recalculation was not performed.");
            return null;
        }

        long startTime = System.currentTimeMillis();

        nonLearnedQuestions.clear();
        double poweredProbFactorSum = 0;
        int learnedIndexes = 0;
        List<Question> limitedQuestions = allQuestionMap.values().stream().filter(q -> !isStudied(q))
                .limit(LIMITED_QUESTION_NUMBER)
                .sorted(Comparator.comparing(Question::getCreated))
                .collect(Collectors.toList());
        List<Question> studiedQuestions = allQuestionMap.values().stream()
                .filter(this::isStudied).collect(Collectors.toList());
        limitedQuestions.addAll(studiedQuestions);
        List<Question> questions = new ArrayList<>(allQuestionMap.values());
        Collections.shuffle(questions);
        for (Question question : limitedQuestions) {
            double poweredProbFactor;
            QuestionLikelihood questionLikelihood =
                    new QuestionLikelihood(
                            question.getId(),
                            question.getWords().toString(),
                            question.getStudyPoints(),
                            poweredProbFactor = Math.pow(question.getStudyPoints(), power),
                            -1,
                            -1,
                            -1,
                            -1
                    );
            if (isStudied(question)) {
                learnedQuestions.put(learnedIndexes++, questionLikelihood);
                continue;   // Do not perform calculation for already learned questions
            }
            nonLearnedQuestions.put(questionLikelihood.getQuestionId(), questionLikelihood);
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
                questions.size(),
                learnedQuestions.values().size(),
                System.currentTimeMillis() - startTime,
                index
        );
        currentMaxIndex = index;
        return Arrays.asList(nonLearnedQuestions, learnedQuestions);
    }

    private boolean isStudied(Question question) {
        return question.getStudyPoints() <= LEARNED_QUESTION_PROB_MARGIN;
    }

    public QuestionDto getRandom() {

        QuestionLikelihood selectedQuestion = null;
        int retryAttempts = 0;
        if (RandomUtils.nextInt(0, 100 * PROBABILITY_ADJUSTING + 1) <= STUDIED_QUESTIONS_PROBABILITY * PROBABILITY_ADJUSTING) {
            Object[] questions = learnedQuestions.values().toArray();
            selectedQuestion = (QuestionLikelihood) questions[RandomUtils.nextInt(0, questions.length)];
        } else {
            do {
                retryAttempts++;
                long currentRange = RandomUtils.nextLong(0, currentMaxIndex + 1);
                for (QuestionLikelihood question : nonLearnedQuestions.values()) {
                    if (question.isInRange(currentRange)) {
                        if (recentlyAnswered(question.getQuestionId())) {
                            continue;
                        }
                        selectedQuestion = question;
                    }
                }
            } while (selectedQuestion == null && retryAttempts < MAX_RETRY_ATTEMPTS);
        }

        if (selectedQuestion == null) {
            throw new RuntimeException(String.format("No question was selected, retry attempts: %d", retryAttempts));
        }

        final Question question = allQuestionMap.get(selectedQuestion.getQuestionId());

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
            allQuestionMap.put(questionToRollback.getId(), questionToRollback.toBuilder().build());
        } else {
            answeredQuestion = allQuestionMap.get(answeredQuestionDto.getId()).toBuilder().build();
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
        answers.add(0, answer);

        return questionMapper.toDto(updatedQuestion);
    }

    private boolean recentlyAnswered(int id) {
        List<Answer> lastAnswers = answers.stream()
            .skip(answers.size() < 100 ? 0 : answers.size() - 100)
            .collect(Collectors.toList());
        for (int i = 0; i < lastAnswers.size(); i++) {
            if (id == lastAnswers.get(i).getQuestion().getId()) {
                return true;
            }
        }
        return false;
    }

    private Question rightAnswer(Question answeredQuestion, boolean rollback) {

        Double previousStudyPoints = answeredQuestion.getStudyPoints();
        Double previousStudyPointsMultiplier = answeredQuestion.getStudyPointsMultiplier();
        if (!isStudied(answeredQuestion)) {
            answeredQuestion.setStudyPoints(previousStudyPoints - RIGHT_ANSWER_DECREMENT * previousStudyPointsMultiplier);
            answeredQuestion.setStudyPointsMultiplier(
                    previousStudyPointsMultiplier == STUDY_POINTS_INIT_MULTIPLIER ? STUDY_POINTS_MULTIPLIER :
                            previousStudyPointsMultiplier * STUDY_POINTS_MULTIPLIER
            );
        }

        LOGGER.info(String.format(
                "%sRight answered the question %d %s probFactor: [%#.1f => %#.1f], probMultiplier: [%#.2f => %#.2f]",
                rollback ? "Rollbacked with " : "", answeredQuestion.getId(), answeredQuestion.getWords(),
                previousStudyPoints, answeredQuestion.getStudyPoints(),
                previousStudyPointsMultiplier, answeredQuestion.getStudyPointsMultiplier()
        ));

        answeredQuestion.setLastAccessed(LocalDateTime.now());
        Question updatedQuestion = questionRepository.saveAndFlush(answeredQuestion);
        allQuestionMap.put(updatedQuestion.getId(), updatedQuestion);
        recalculateRandomizer(POWER);
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

        answeredQuestion.setLastAccessed(LocalDateTime.now());
        Question updatedQuestion = questionRepository.saveAndFlush(answeredQuestion);
        allQuestionMap.put(updatedQuestion.getId(), updatedQuestion);
        recalculateRandomizer(POWER);
        return updatedQuestion;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class QuestionLikelihood {
        int questionId;
        String words;
        double probabilityFactor;
        double poweredProbabilityFactor;
        double percentage;
        long range;
        long indexStart = -1;
        long indexEnd = -1;

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
