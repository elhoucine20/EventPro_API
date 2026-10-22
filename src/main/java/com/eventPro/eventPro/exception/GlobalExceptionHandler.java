package com.eventPro.eventPro.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice // utilisant quand exception dans une controller
public class GlobalExceptionHandler {

    // email used
    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ErrorResponse> handelEmailUsed(EmailAlreadyUsedException exception){
        return build(HttpStatus.CONFLICT,exception.getMessage(),null);
    }

    // not found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handelNotFound(ResourceNotFoundException exception){
        return build(HttpStatus.NOT_FOUND,exception.getMessage(),null);
    }


    // coté validation
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception){
        Map<String,String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(e->errors.putIfAbsent(e.getField(),e.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST,"les donnees invalide",errors);
    }

    // quand 2 inscriptions simultanees avec la meme email
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException exception){
        return build(HttpStatus.CONFLICT,"conflit avec les donnees existantes",null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, Map<String, String> errors) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(LocalDateTime.now(), status.value(), message, errors));
    }


    // commence a event
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException exception){
        return build(HttpStatus.BAD_REQUEST,exception.getMessage(),null);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForBidden(ForbiddenOperationException exception){
        return build(HttpStatus.FORBIDDEN,exception.getMessage(),null);
    }

}
