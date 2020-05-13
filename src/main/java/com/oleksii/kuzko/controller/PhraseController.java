package com.oleksii.kuzko.controller;

import com.oleksii.kuzko.model.Phrase;
import com.oleksii.kuzko.service.PhraseService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author The Weather Company, An IBM Business
 */
@RestController
@RequestMapping("/phrase")
public class PhraseController {

    private final PhraseService phraseService;

    public PhraseController(PhraseService phraseService) {
        this.phraseService = phraseService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Phrase> getRandom() {
        Phrase foundPhrase = phraseService.getRandom();
        if (foundPhrase == null) {
            return ResponseEntity.notFound().build();
        } else {
            return ResponseEntity.ok(foundPhrase);
        }
    }

    @GetMapping(value = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Phrase>> getAll() {
        return ResponseEntity.ok(phraseService.getAll());
    }

    @GetMapping(value = "/allMysql", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Phrase>> getAllMysql() {
        return ResponseEntity.ok(phraseService.getAllMysql());
    }

    @GetMapping(value = "/copy", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity copy() {
        return ResponseEntity.ok(phraseService.copy());
    }

}