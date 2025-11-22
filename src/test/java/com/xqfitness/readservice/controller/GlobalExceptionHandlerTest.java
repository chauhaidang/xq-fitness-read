package com.xqfitness.readservice.controller;

import com.xqfitness.readservice.dto.ErrorDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleException - should return HTTP 500 status for generic exception")
    void handleException_shouldReturnHttp500StatusForGenericException() {
        // Given
        Exception exception = new Exception("Test error message");

        // When
        ResponseEntity<ErrorDTO> response = exceptionHandler.handleException(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("handleException - should return ErrorDTO with correct error code")
    void handleException_shouldReturnErrorDTOWithCorrectErrorCode() {
        // Given
        Exception exception = new Exception("Test error message");

        // When
        ResponseEntity<ErrorDTO> response = exceptionHandler.handleException(exception);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("handleException - should return ErrorDTO with exception message")
    void handleException_shouldReturnErrorDTOWithExceptionMessage() {
        // Given
        String errorMessage = "Database connection failed";
        Exception exception = new Exception(errorMessage);

        // When
        ResponseEntity<ErrorDTO> response = exceptionHandler.handleException(exception);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("handleException - should set timestamp in ErrorDTO")
    void handleException_shouldSetTimestampInErrorDTO() {
        // Given
        Exception exception = new Exception("Test error");
        LocalDateTime beforeCall = LocalDateTime.now().minusSeconds(1);

        // When
        ResponseEntity<ErrorDTO> response = exceptionHandler.handleException(exception);
        LocalDateTime afterCall = LocalDateTime.now().plusSeconds(1);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTimestamp()).isNotNull();
        assertThat(response.getBody().getTimestamp()).isBetween(beforeCall, afterCall);
    }

    @Test
    @DisplayName("handleException - should handle RuntimeException")
    void handleException_shouldHandleRuntimeException() {
        // Given
        RuntimeException exception = new RuntimeException("Runtime error occurred");

        // When
        ResponseEntity<ErrorDTO> response = exceptionHandler.handleException(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("Runtime error occurred");
    }

    @Test
    @DisplayName("handleException - should handle NullPointerException")
    void handleException_shouldHandleNullPointerException() {
        // Given
        NullPointerException exception = new NullPointerException("Null value encountered");

        // When
        ResponseEntity<ErrorDTO> response = exceptionHandler.handleException(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("Null value encountered");
    }

    @Test
    @DisplayName("handleException - should handle IllegalArgumentException")
    void handleException_shouldHandleIllegalArgumentException() {
        // Given
        IllegalArgumentException exception = new IllegalArgumentException("Invalid argument provided");

        // When
        ResponseEntity<ErrorDTO> response = exceptionHandler.handleException(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid argument provided");
    }

    @Test
    @DisplayName("handleException - should handle exception with null message")
    void handleException_shouldHandleExceptionWithNullMessage() {
        // Given
        Exception exception = new Exception((String) null);

        // When
        ResponseEntity<ErrorDTO> response = exceptionHandler.handleException(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).isNull();
    }
}