package com.guessword.dto.mapper;

import java.util.List;

import com.guessword.dto.QuestionDto;
import com.guessword.entity.Question;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

@Mapper(uses = WordMapper.class, builder = @Builder(disableBuilder = true))     //Disable builders as we need to use overridden setter in Question class
public interface QuestionMapper {
    QuestionDto toDto(Question question);
    Question toEntity(QuestionDto questionDto);
    List<QuestionDto> toDto(List<Question> questions);
}
