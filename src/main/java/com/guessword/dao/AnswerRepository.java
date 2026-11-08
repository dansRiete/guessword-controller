package com.guessword.dao;

import com.guessword.entity.Answer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Integer> {
    Optional<Answer> findFirstByQuestionIdOrderByAnsweredDesc(Integer questionId);
}
