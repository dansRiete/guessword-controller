package com.guessword.dto.mapper;

import com.guessword.dto.WordDto;
import com.guessword.entity.Word;
import org.mapstruct.Mapper;

@Mapper
public interface WordMapper {
    WordDto toDto(Word question);
    Word toEntity(WordDto questionDto);
}
