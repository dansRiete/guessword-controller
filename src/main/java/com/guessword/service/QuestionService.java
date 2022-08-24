package com.guessword.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.guessword.dao.QuestionRepository;
import com.guessword.dto.QuestionDto;
import com.guessword.dto.mapper.QuestionMapper;
import com.guessword.entity.Question;
import com.guessword.exception.DataNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestionService.class);

    public static final String COULD_NOT_FIND_A_QUESTION_WITH_ID = "Could not find a question with id %d";

    private final QuestionRepository questionRepository;
    private final QuestionMapper questionMapper;

    private Map<Integer, Question> questionsCache;

    public QuestionService(final QuestionRepository questionRepository, final QuestionMapper questionMapper) {
        this.questionRepository = questionRepository;
        this.questionMapper = questionMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        questionsCache = questionRepository.findAll().stream().collect(
            Collectors.toMap(Question::getId, Function.identity())
        );
    }

    public Question updateQuestion(QuestionDto questionDto){
        return updateQuestion(questionMapper.toEntity(questionDto));
    }
    public Question updateQuestion(Question question) {
        long start = System.currentTimeMillis();
        questionsCache.put(question.getId(), question);
        Question questionToUpdate = questionRepository.findById(question.getId())
                .orElseThrow(() -> new DataNotFoundException(
                        String.format(COULD_NOT_FIND_A_QUESTION_WITH_ID, question.getId())
                ));
        Question updatedQuestion = questionRepository.save(question);
        LOGGER.info("Updating the question: {}, after update: {}, time: {}", questionToUpdate, updatedQuestion,
            System.currentTimeMillis() - start);
        return question;
    }

    public QuestionDto createQuestion(QuestionDto questionDto) {
        Question question = questionMapper.toEntity(questionDto);
        question.setCreated(ZonedDateTime.now());
        question.setStudyPoints(30.0);
        question.setId(null);
        question.setStudyPointsMultiplier(1.0);
        Question createdQuestion = questionRepository.save(question);
        questionsCache.put(createdQuestion.getId(), createdQuestion);
        LOGGER.info("Created a question: {}", createdQuestion);
        return questionMapper.toDto(question);
    }

    public QuestionDto findById(Integer questionId) {
        return questionRepository.findById(questionId).map(questionMapper::toDto)
            .orElseThrow(() -> new DataNotFoundException(String.format(COULD_NOT_FIND_A_QUESTION_WITH_ID, questionId)));
    }

    public List<QuestionDto> findAll() {
        List<Question> allQuestions = questionRepository.findAll();
        LOGGER.info("findAll questions method found {} records", allQuestions.size());
        return questionMapper.toDto(allQuestions);
    }

    public void deleteQuestion(Integer questionId) {
        questionsCache.remove(questionId);
        Question questionToDelete = questionRepository.findById(questionId).orElseThrow(
            () -> new DataNotFoundException(String.format(COULD_NOT_FIND_A_QUESTION_WITH_ID, questionId)));
        questionRepository.delete(questionToDelete);
        LOGGER.info("Question deleted {}", questionToDelete);
    }

    public Map<Integer, Question> getQuestionsCache() {
        return questionsCache;
    }

    public QuestionDto createQuestion(String questionsToCreate) {
        return null;
    }
}
