package com.guessword.controller;

import com.guessword.dto.QuestionDto;
import com.guessword.dto.mapper.QuestionMapper;
import com.guessword.service.QuestionEngine;
import com.guessword.dao.QuestionRepository;
import com.guessword.service.QuestionEngine.QuestionLikelihood;
import com.guessword.service.QuestionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/question")
public class QuestionController {

    private final QuestionEngine questionEngine;
    private final QuestionService questionService;

    public QuestionController(
        final QuestionEngine questionEngine,
        final QuestionRepository questionRepository,
        final QuestionMapper questionMapper,
        final QuestionService questionService
    ) {
        this.questionEngine = questionEngine;
        this.questionService = questionService;
    }

    @GetMapping
    public ResponseEntity<List<QuestionDto>> findAll() {
        return ResponseEntity.ok(questionService.findAll());
    }

    @GetMapping("/{questionId}")
    public ResponseEntity<QuestionDto> findById(@PathVariable Integer questionId) {
        return ResponseEntity.ok(questionService.findById(questionId));
    }

    @PutMapping("/{questionId}")
    public ResponseEntity<QuestionDto> update(@PathVariable Integer questionId, @RequestBody QuestionDto questionToUpdate) {
        questionToUpdate.setId(questionId);
        return ResponseEntity.ok(questionService.updateQuestion(questionToUpdate));
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

    @GetMapping(value = "/mysql", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<QuestionDto>> findAllMysql() {
        final List<QuestionDto> allMysqlQuestions = questionEngine.findAllMysql();
        return ResponseEntity.ok(allMysqlQuestions);
    }

    @GetMapping(value = "/copyFromMysql", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Boolean> copy() {
        return ResponseEntity.ok(questionEngine.copyAll());
    }

    @GetMapping(value = "/recalculate/{power}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<Integer, QuestionLikelihood>>> recalculate(@PathVariable Double power) {
        return ResponseEntity.ok(questionEngine.recalculateRandomizer(power));
    }

}