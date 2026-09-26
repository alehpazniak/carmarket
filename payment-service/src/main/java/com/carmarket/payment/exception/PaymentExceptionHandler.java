package com.carmarket.payment.exception;

import com.carmarket.payment.przelewy24.Przelewy24Exception;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<Map<String, String>> handlePayment(PaymentException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Przelewy24Exception.class)
    public ResponseEntity<Map<String, String>> handleP24(Przelewy24Exception e) {
        log.error("Przelewy24 error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(Map.of("error", "Payment provider error, please try again later"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .orElse("Invalid request");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
