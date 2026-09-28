package io.github.hantsy.jdbc.tx.it.service;

public class CustomCheckedException extends Exception {
    public CustomCheckedException() {
        super("custom checked");
    }
}
