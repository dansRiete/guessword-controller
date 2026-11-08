package com.guessword.dao;

import com.guessword.entity.Answer;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Integer> {
    Optional<Answer> findFirstByQuestionIdOrderByAnsweredDesc(Integer questionId);

    @EntityGraph(attributePaths = {"question"})
    List<Answer> findByAnsweredGreaterThanEqualOrderByAnsweredAsc(ZonedDateTime startOfDay);
}
