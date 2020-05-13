package com.guessword.dto.mapper;

import java.util.List;

import com.guessword.dto.QuestionDto;
import com.guessword.entity.Question;
import org.mapstruct.Mapper;

@Mapper(uses = WordMapper.class)
public interface QuestionMapper {
    QuestionDto toDto(Question question);
    Question toEntity(QuestionDto questionDto);
    List<QuestionDto> toDto(List<Question> questions);
}
