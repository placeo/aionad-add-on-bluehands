package com.skt.aionad.addon.utils;

import android.util.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import timber.log.Timber;

public class ColorLogTree extends Timber.DebugTree {

    private static final String LOG_COLOR_RESET = "\033[0m";
    private static final String LOG_COLOR_ERROR = "\033[31m";    // Red
    private static final String LOG_COLOR_WARNING = "\033[33m"; // Yellow
    private static final String LOG_COLOR_INFO = "\033[32m";     // Green
    private static final String LOG_COLOR_DEBUG = "\033[36m";    // Cyan
    private static final String LOG_COLOR_VERBOSE = "\033[35m"; // Magenta

    private final int minLogLevel;

    public ColorLogTree() {
        this.minLogLevel = Log.VERBOSE; // Default to log everything
    }

    public ColorLogTree(@NotNull String minLogLevelStr) {
        this.minLogLevel = parseLogLevel(minLogLevelStr.toUpperCase());
    }

    private int parseLogLevel(String level) {
        switch (level) {
            case "VERBOSE": return Log.VERBOSE;
            case "DEBUG": return Log.DEBUG;
            case "INFO": return Log.INFO;
            case "WARN": return Log.WARN;
            case "ERROR": return Log.ERROR;
            default:
                Log.w("ColorLogTree", "Unknown log level '" + level + "', defaulting to DEBUG.");
                return Log.DEBUG;
        }
    }

    @Override
    protected boolean isLoggable(@Nullable String tag, int priority) {
        return priority >= minLogLevel;
    }

    @Override
    protected void log(int priority, @Nullable String tag, @NotNull String message, @Nullable Throwable t) {
        String color;
        switch (priority) {
            case Log.ERROR:
                color = LOG_COLOR_ERROR;
                break;
            case Log.WARN:
                color = LOG_COLOR_WARNING;
                break;
            case Log.INFO:
                color = LOG_COLOR_INFO;
                break;
            case Log.DEBUG:
                color = LOG_COLOR_DEBUG;
                break;
            default:
                color = LOG_COLOR_VERBOSE;
                break;
        }

        // Add thread info to the message
        String threadName = Thread.currentThread().getName();
        String newMessage = "[" + threadName + "] " + message;

        super.log(priority, tag, color + newMessage + LOG_COLOR_RESET, t);
    }

    @Override
    protected @Nullable String createStackElementTag(@NotNull StackTraceElement element) {
        // Use a fixed tag for all logs
        return "PHOTO-BOX-FRONTEND";
    }
} 