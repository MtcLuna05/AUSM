package com.luna.ausm.impl.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class NoOpLogger {
    public static final NoOpLogger INSTANCE = new NoOpLogger();
    private static final Logger LOGGER = LogManager.getLogger("AUSM");

    private NoOpLogger() {
    }

    public void debug(String message, Object... arguments) {
        LOGGER.debug(message, arguments);
    }

    public void info(String message, Object... arguments) {
        LOGGER.info(message, arguments);
    }

    public void warn(String message, Object... arguments) {
        LOGGER.warn(message, arguments);
    }

    public void error(String message, Object... arguments) {
        LOGGER.error(message, arguments);
    }

    public void debug(Object message) {
        LOGGER.debug(message);
    }

    public void info(Object message) {
        LOGGER.info(message);
    }

    public void warn(Object message) {
        LOGGER.warn(message);
    }

    public void error(Object message) {
        LOGGER.error(message);
    }
}
