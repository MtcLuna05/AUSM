package com.l.ausm.impl.util;

public final class NoOpLogger {
    public static final NoOpLogger INSTANCE = new NoOpLogger();

    private NoOpLogger() {
    }

    public void debug(String message, Object... arguments) {
    }

    public void info(String message, Object... arguments) {
    }

    public void warn(String message, Object... arguments) {
    }

    public void error(String message, Object... arguments) {
    }

    public void debug(Object message) {
    }

    public void info(Object message) {
    }

    public void warn(Object message) {
    }

    public void error(Object message) {
    }
}
