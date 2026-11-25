package com.marketplace.exception;

/** Unchecked: bad user input is a programming/validation error, caught once in the UI loop. */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
