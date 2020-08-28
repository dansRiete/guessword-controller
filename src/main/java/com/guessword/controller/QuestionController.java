package com.guessword.controller;

import java.util.List;

import com.guessword.dto.QuestionDto;
import com.guessword.service.QuestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/question")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(final QuestionService questionService) {
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

    @DeleteMapping("/{questionId}")
    public ResponseEntity<?> delete(@PathVariable Integer questionId) {
        questionService.deleteQuestion(questionId);
        return ResponseEntity.ok().build();
    }

    /*@GetMapping(value = "/mysql", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<QuestionDto>> findAllMysql() {
        final List<QuestionDto> allMysqlQuestions = questionEngine.findAllMysql();
        return ResponseEntity.ok(allMysqlQuestions);
    }*/

    /*@GetMapping(value = "/copyFromMysql", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Boolean> copy() {
        return ResponseEntity.ok(questionEngine.copyAll());
    }*/

    /*@GetMapping(value = "/recalculate/{power}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<Integer, QuestionLikelihood>>> recalculate(@PathVariable Double power) {
        return ResponseEntity.ok(questionEngine.recalculateRandomizer(power));
    }*/

}