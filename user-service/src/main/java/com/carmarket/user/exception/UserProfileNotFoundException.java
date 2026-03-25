package com.carmarket.user.exception;

public class UserProfileNotFoundException extends RuntimeException {

    public UserProfileNotFoundException(String massage) {
        super(massage);
    }

    public UserProfileNotFoundException() {
    }
}
