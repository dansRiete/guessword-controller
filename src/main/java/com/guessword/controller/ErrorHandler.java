package com.guessword.controller;

import com.guessword.exception.DataNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<?> handle(Throwable ex) {
        LOGGER.error(ex.getLocalizedMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getLocalizedMessage());
    }

    @ExceptionHandler(DataNotFoundException.class)
    public ResponseEntity<?> handle(DataNotFoundException ex) {
        LOGGER.warn(ex.getLocalizedMessage(), ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getLocalizedMessage());
    }


}
