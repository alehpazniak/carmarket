package com.carmarket.car.exception;

public class AccessDeniedException extends RuntimeException{

    public AccessDeniedException(String msg) {
        super(msg);
    }
}
