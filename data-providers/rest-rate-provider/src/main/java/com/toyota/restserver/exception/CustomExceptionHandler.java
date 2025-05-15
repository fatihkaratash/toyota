package com.toyota.restserver.exception;

import com.toyota.restserver.logging.LoggingHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class CustomExceptionHandler extends ResponseEntityExceptionHandler {

    private static final LoggingHelper log = new LoggingHelper(CustomExceptionHandler.class);

    @ExceptionHandler(RateNotFoundException.class)
    public ResponseEntity<Object> handleRateNotFoundException(
            RateNotFoundException ex, WebRequest request) {
        log.warn(LoggingHelper.OPERATION_ERROR, LoggingHelper.PLATFORM_REST, "KurBulunamadi", null, ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", "Bulunamadi");
        body.put("message", ex.getMessage());
        body.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(
            Exception ex, WebRequest request) {
        log.error(LoggingHelper.OPERATION_ERROR, LoggingHelper.PLATFORM_REST, "GenelHata", null, 
                "Beklenmeyen bir hata olustu: " + ex.getMessage(), ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Ic Sunucu Hatasi");
        body.put("message", "Beklenmeyen bir hata olustu. Lutfen daha sonra tekrar deneyiniz.");
        body.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
