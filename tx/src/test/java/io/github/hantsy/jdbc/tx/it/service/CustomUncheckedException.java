package io.github.hantsy.jdbc.tx.it.service;

public class CustomUncheckedException extends RuntimeException {
    public CustomUncheckedException() {
        super("custom unchecked");
    }
}
