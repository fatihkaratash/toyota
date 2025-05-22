package com.toyota.mainapp.exception;

import com.toyota.mainapp.cache.exception.CacheException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler for REST API endpoints.
 * Provides consistent error response format and centralized logging.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle general exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneralException(Exception ex, WebRequest request) {
        logger.error("İşlem sırasında beklenmedik hata: {}", ex.getMessage(), ex);
        return buildErrorResponse("Beklenmedik bir hata oluştu", 
                                 ex.getMessage(), 
                                 HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * Handle CacheException from RateCache operations.
     */
    @ExceptionHandler(CacheException.class)
    public ResponseEntity<Object> handleCacheException(CacheException ex, WebRequest request) {
        logger.error("Önbellek işlemi sırasında hata: {}", ex.getMessage(), ex);
        return buildErrorResponse("Önbellek işlemi başarısız oldu", 
                                 ex.getMessage(), 
                                 HttpStatus.SERVICE_UNAVAILABLE);
    }
    
    /**
     * Handle ValidationException for rate validation failures.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Object> handleValidationException(ValidationException ex, WebRequest request) {
        logger.warn("Doğrulama hatası: {}", ex.getMessage());
        return buildErrorResponse("Doğrulama hatası", 
                                 ex.getMessage(), 
                                 HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Handle NotFoundException for missing resources.
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleNotFoundException(NotFoundException ex, WebRequest request) {
        logger.warn("Kaynak bulunamadı hatası: {}", ex.getMessage());
        return buildErrorResponse("Kaynak bulunamadı", 
                                 ex.getMessage(), 
                                 HttpStatus.NOT_FOUND);
    }
    
    /**
     * Builds a standardized error response.
     */
    private ResponseEntity<Object> buildErrorResponse(String error, String message, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        return new ResponseEntity<>(body, status);
    }
}
