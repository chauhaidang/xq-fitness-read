package com.xqfitness.readservice.controller;

import com.xqfitness.readservice.dto.ErrorDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorDTO> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = ex.getName() != null
                ? "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue()
                : "Invalid parameter value";
        ErrorDTO error = new ErrorDTO("BAD_REQUEST", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorDTO> handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        ErrorDTO error = new ErrorDTO("BAD_REQUEST", "Missing required parameter: " + ex.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDTO> handleIllegalArgumentException(IllegalArgumentException ex) {
        ErrorDTO error = new ErrorDTO("NOT_FOUND", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDTO> handleException(Exception ex) {
        ErrorDTO error = new ErrorDTO("INTERNAL_ERROR", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
