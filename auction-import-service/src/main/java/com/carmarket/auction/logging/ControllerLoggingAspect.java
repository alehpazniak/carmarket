package com.carmarket.auction.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Logs the request (HTTP method, URI, controller arguments) and the response (status, body)
 * of {@code ApibaraController} and {@code AuctionLotController} as JSON.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ControllerLoggingAspect {

    private final ObjectMapper objectMapper;

    @Around("within(com.carmarket.auction.controller.ApibaraController) "
        + "|| within(com.carmarket.auction.controller.AuctionLotController)")
    public Object logRequestAndResponse(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String endpoint = signature.getDeclaringType().getSimpleName() + "." + signature.getName();

        Map<String, Object> request = new LinkedHashMap<>();
        HttpServletRequest httpRequest = currentRequest();
        if (httpRequest != null) {
            request.put("method", httpRequest.getMethod());
            request.put("uri", httpRequest.getRequestURI());
        }
        Map<String, Object> args = new LinkedHashMap<>();
        String[] names = signature.getParameterNames();
        Object[] values = joinPoint.getArgs();
        for (int i = 0; i < values.length; i++) {
            args.put(names != null ? names[i] : "arg" + i, values[i]);
        }
        request.put("args", args);
        log.info("{} request: {}", endpoint, toJson(request));

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            Map<String, Object> response = new LinkedHashMap<>();
            if (result instanceof ResponseEntity<?> entity) {
                response.put("status", entity.getStatusCode().value());
                response.put("body", loggableBody(entity.getBody()));
            } else {
                response.put("body", loggableBody(result));
            }
            response.put("durationMs", System.currentTimeMillis() - start);
            log.info("{} response: {}", endpoint, toJson(response));
            return result;
        } catch (Throwable e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("exception", e.getClass().getName());
            error.put("message", e.getMessage());
            error.put("durationMs", System.currentTimeMillis() - start);
            log.warn("{} failed: {}", endpoint, toJson(error));
            throw e;
        }
    }

    /** Binary payloads (image proxy) are logged by size only. */
    private Object loggableBody(Object body) {
        if (body instanceof byte[] bytes) {
            return Map.of("bytes", bytes.length);
        }
        return body;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "\"<unserializable: " + e.getMessage() + ">\"";
        }
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
            ? attrs.getRequest()
            : null;
    }
}
