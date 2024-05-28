package com.guessword.controller;

import com.guessword.dto.QuestionDto;
import com.guessword.service.QuestionEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/answer")
public class AnswerController {

    private final QuestionEngine questionEngine;

    public AnswerController(final QuestionEngine questionEngine) {
        this.questionEngine =  questionEngine;
    }

    @GetMapping(value = "/getNext")
    public ResponseEntity<QuestionDto> getRandom() {
        QuestionDto foundQuestion = questionEngine.getRandom();
        if (foundQuestion == null) {
            return ResponseEntity.status(500).build();
        } else {
            return ResponseEntity.ok(foundQuestion);
        }
    }

    @PostMapping(value = "/right")
    public ResponseEntity<QuestionDto> rightAnswer(@RequestBody QuestionDto answeredQuestionDto) {
        QuestionDto answer = questionEngine.answer(answeredQuestionDto, true, false);
        return ResponseEntity.ok(answer);
    }

    @PostMapping(value = "/wrong")
    public ResponseEntity<QuestionDto> wrongAnswer(@RequestBody QuestionDto answeredQuestionDto) {
        QuestionDto answer = questionEngine.answer(answeredQuestionDto, false, false);
        return ResponseEntity.ok(answer);
    }

    @PostMapping(value = "/previousRight")
    public ResponseEntity<QuestionDto> previousRight() {
        QuestionDto answer = questionEngine.answer(null, true, true);
        return ResponseEntity.ok(answer);
    }

    @PostMapping(value = "/previousWrong")
    public ResponseEntity<QuestionDto> previousWrong() {
        QuestionDto answer = questionEngine.answer(null, false, true);
        return ResponseEntity.ok(answer);
    }

}
