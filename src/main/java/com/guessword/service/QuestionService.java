package com.guessword.service;

import java.util.List;

import com.guessword.dao.QuestionRepository;
import com.guessword.dto.QuestionDto;
import com.guessword.dto.mapper.QuestionMapper;
import com.guessword.entity.Question;
import com.guessword.exception.DataNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QuestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestionService.class);

    public static final String COULD_NOT_FIND_A_QUESTION_WITH_ID = "Could not find a question with id %d";

    private final QuestionRepository questionRepository;
    private final QuestionMapper questionMapper;

    public QuestionService(final QuestionRepository questionRepository, final QuestionMapper questionMapper) {
        this.questionRepository = questionRepository;
        this.questionMapper = questionMapper;
    }

    public QuestionDto updateQuestion(QuestionDto questionDto) {
        questionRepository.findById(questionDto.getId())
            .orElseThrow(() -> new DataNotFoundException(
                String.format(COULD_NOT_FIND_A_QUESTION_WITH_ID, questionDto.getId())
            ));
        Question savedQuestion = questionRepository.save(questionMapper.toEntity(questionDto));
        LOGGER.info("Question updated {}", savedQuestion);
        return questionMapper.toDto(savedQuestion);
    }

    public QuestionDto findById(Integer questionId) {
        return questionRepository.findById(questionId).map(questionMapper::toDto)
            .orElseThrow(() -> new DataNotFoundException(String.format(COULD_NOT_FIND_A_QUESTION_WITH_ID, questionId)));
    }

    public List<QuestionDto> findAll() {
        return questionMapper.toDto(questionRepository.findAll());
    }
}
