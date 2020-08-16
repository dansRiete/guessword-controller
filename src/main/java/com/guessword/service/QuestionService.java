package com.guessword.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.guessword.dao.AnswerRepository;
import com.guessword.dao.QuestionDao;
import com.guessword.dao.QuestionRepository;
import com.guessword.dto.QuestionDto;
import com.guessword.dto.mapper.QuestionMapper;
import com.guessword.entity.Answer;
import com.guessword.entity.BaseQuestion;
import com.guessword.entity.Question;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.guessword.utils.ApplicationConstants.PROBABILITY_ADJUSTING;
import static com.guessword.utils.ApplicationConstants.RIGHT_ANSWER_DECREMENT;
import static com.guessword.utils.ApplicationConstants.STUDIED_QUESTIONS_PROBABILITY;
import static com.guessword.utils.ApplicationConstants.STUDY_POINTS_INIT_MULTIPLIER;
import static com.guessword.utils.ApplicationConstants.STUDY_POINTS_MULTIPLIER;
import static com.guessword.utils.ApplicationConstants.WRONG_ANSWER_INCREMENT;

/**
 * Contains all the logic for randomizing questions, ensuring the calculation of the probability of asking questions
 * depending on external conditions such as: the number of questions already studied and not studied, the category of
 * currently selected questions and others ...
 *
 * @author Oleksii Kuzko. 9 April 2020.
 */
@Service
public class QuestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestionService.class);

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
        randomizingIndexesMaxRange = allQuestionMap.values().size() * PROBABILITY_ADJUSTING;
        recalculateRandomizer(randomizingFactor);
        LOGGER.info(String.format("QuestionEngine has been initialized. Total questions in database: %d. Time taken: %d ms.",
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
        List<Question> alreadyAnsweredQuestions = answers.stream().limit(nonRepeatableQuestions)
                .map(Answer::getQuestion).collect(Collectors.toList());
        List<Question> limitedNonStudiedQuestions = allQuestionMap.values().stream()
                .filter(q -> !isStudied(q))
                .filter(q -> !alreadyAnsweredQuestions.contains(q))
                .sorted(Comparator.comparing(Question::getCreated))
                .limit(activeQuestionsNumber)
                .collect(Collectors.toList());
        List<Question> studiedQuestions = allQuestionMap.values().stream()
                .filter(this::isStudied).collect(Collectors.toList());
        List<Question> activeQuestions = Stream.concat(limitedNonStudiedQuestions.stream(), studiedQuestions.stream())
            .collect(Collectors.toList());
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

        //  Computing likelihood indexes.
        long index = -1;
        for (QuestionLikelihood question : nonLearnedQuestions.values()) {
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
                learnedQuestions.values().size(),
                activeQuestions.size(),
                System.currentTimeMillis() - startTime,
                index
        );
        currentMaxIndex = index;
        return Arrays.asList(nonLearnedQuestions, learnedQuestions);
    }

    private boolean isStudied(Question question) {
        return question.getStudyPoints() <= learnedStudyPointsThreshold;
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

        Answer answer = Answer.builder().answered(ZonedDateTime.now(ZoneId.of("UTC")))
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

        // todo should we move setting lastAccessed to getRandom method?
        answeredQuestion.setLastAccessed(ZonedDateTime.now(ZoneId.of("UTC")));
        Question updatedQuestion = questionRepository.saveAndFlush(answeredQuestion);
        allQuestionMap.put(updatedQuestion.getId(), updatedQuestion);
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
        Question updatedQuestion = questionRepository.saveAndFlush(answeredQuestion);
        allQuestionMap.put(updatedQuestion.getId(), updatedQuestion);
        recalculateRandomizer(randomizingFactor);
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
