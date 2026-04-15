package com.smartcampus.exception;

public class SensorUnavailableException extends RuntimeException {
    public SensorUnavailableException(String message) {
        super(message);
    }
}