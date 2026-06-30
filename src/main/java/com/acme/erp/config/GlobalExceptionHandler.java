package com.acme.erp.config;

import com.acme.erp.exception.*;
import com.acme.erp.dto.Dtos;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Dtos.ErrorResponse> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Dtos.ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Dtos.ErrorResponse> handleValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Dtos.ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Dtos.ErrorResponse> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Dtos.ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Dtos.ErrorResponse> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new Dtos.ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Dtos.ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Dtos.ErrorResponse(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Dtos.ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Dtos.ErrorResponse(ex.getMessage()));
    }
}
