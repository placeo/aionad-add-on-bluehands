package com.skt.aionad.addon.utils;

import android.content.Context;
import android.util.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import timber.log.Timber;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 파일 기반 로깅을 위한 Timber Tree 구현
 * 로그 파일 회전(rotation) 및 크기 제한 기능 포함
 */
public class FileLoggingTree extends Timber.DebugTree {

    private static final String LOG_FILE_PREFIX = "aionad-addon";
    private static final String LOG_FILE_EXTENSION = ".log";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());

    private final Context context;
    private final int minLogLevel;
    private final long maxFileSize;
    private final int maxBackupIndex;
    private final File logDir;

    public FileLoggingTree(Context context) {
        this(context, "DEBUG");
    }

    public FileLoggingTree(Context context, @NotNull String minLogLevelStr) {
        this.context = context;
        this.minLogLevel = parseLogLevel(minLogLevelStr.toUpperCase());
        
        // ConfigManager에서 설정값 가져오기
        ConfigManager config = ConfigManager.getInstance();
        this.maxFileSize = config.getLogMaxFileSize();
        this.maxBackupIndex = config.getLogMaxBackupIndex();
        
        // 로그 디렉토리 설정 (앱의 files 디렉토리 하위에 logs 폴더)
        this.logDir = new File(context.getFilesDir(), "logs");
        
        // 로그 디렉토리가 없으면 생성
        if (!logDir.exists()) {
            if (!logDir.mkdirs()) {
                Log.e("FileLoggingTree", "Failed to create log directory: " + logDir.getAbsolutePath());
            }
        }
    }

    private int parseLogLevel(String level) {
        switch (level) {
            case "VERBOSE": return Log.VERBOSE;
            case "DEBUG": return Log.DEBUG;
            case "INFO": return Log.INFO;
            case "WARN": return Log.WARN;
            case "ERROR": return Log.ERROR;
            default:
                Log.w("FileLoggingTree", "Unknown log level '" + level + "', defaulting to DEBUG.");
                return Log.DEBUG;
        }
    }

    @Override
    protected boolean isLoggable(@Nullable String tag, int priority) {
        return priority >= minLogLevel;
    }

    @Override
    protected void log(int priority, @Nullable String tag, @NotNull String message, @Nullable Throwable t) {
        try {
            String logLevel = priorityToString(priority);
            String timestamp = DATE_FORMAT.format(new Date());
            String threadName = Thread.currentThread().getName();
            
            // 로그 메시지 포맷: [TIMESTAMP] [LEVEL] [THREAD] [TAG] MESSAGE
            StringBuilder logBuilder = new StringBuilder();
            logBuilder.append("[").append(timestamp).append("] ");
            logBuilder.append("[").append(logLevel).append("] ");
            logBuilder.append("[").append(threadName).append("] ");
            if (tag != null) {
                logBuilder.append("[").append(tag).append("] ");
            }
            logBuilder.append(message);
            
            // 예외 정보가 있으면 추가
            if (t != null) {
                logBuilder.append("\n").append(Log.getStackTraceString(t));
            }
            
            logBuilder.append("\n");
            
            writeToFile(logBuilder.toString());
            
        } catch (Exception e) {
            Log.e("FileLoggingTree", "Error writing log to file", e);
        }
    }

    private String priorityToString(int priority) {
        switch (priority) {
            case Log.VERBOSE: return "V";
            case Log.DEBUG: return "D";
            case Log.INFO: return "I";
            case Log.WARN: return "W";
            case Log.ERROR: return "E";
            default: return "?";
        }
    }

    private synchronized void writeToFile(String logMessage) {
        File currentLogFile = getCurrentLogFile();
        
        try {
            // 파일 크기 확인 및 회전
            if (currentLogFile.exists() && currentLogFile.length() >= maxFileSize) {
                rotateLogFiles();
                currentLogFile = getCurrentLogFile();
            }
            
            // 로그 파일에 쓰기
            try (FileWriter writer = new FileWriter(currentLogFile, true)) {
                writer.write(logMessage);
                writer.flush();
            }
            
        } catch (IOException e) {
            Log.e("FileLoggingTree", "Failed to write log to file: " + currentLogFile.getAbsolutePath(), e);
        }
    }

    private File getCurrentLogFile() {
        return new File(logDir, LOG_FILE_PREFIX + LOG_FILE_EXTENSION);
    }

    /**
     * 로그 파일 회전
     * current.log -> current.log.1 -> current.log.2 -> ... -> current.log.maxBackupIndex
     */
    private void rotateLogFiles() {
        try {
            // 가장 오래된 백업 파일부터 삭제
            File oldestBackup = new File(logDir, LOG_FILE_PREFIX + LOG_FILE_EXTENSION + "." + maxBackupIndex);
            if (oldestBackup.exists()) {
                if (!oldestBackup.delete()) {
                    Log.w("FileLoggingTree", "Failed to delete oldest backup file: " + oldestBackup.getAbsolutePath());
                }
            }
            
            // 백업 파일들을 하나씩 뒤로 밀기
            for (int i = maxBackupIndex - 1; i >= 1; i--) {
                File sourceFile = new File(logDir, LOG_FILE_PREFIX + LOG_FILE_EXTENSION + "." + i);
                File targetFile = new File(logDir, LOG_FILE_PREFIX + LOG_FILE_EXTENSION + "." + (i + 1));
                
                if (sourceFile.exists()) {
                    if (!sourceFile.renameTo(targetFile)) {
                        Log.w("FileLoggingTree", "Failed to rename " + sourceFile.getName() + " to " + targetFile.getName());
                    }
                }
            }
            
            // 현재 로그 파일을 .1 백업으로 이동
            File currentFile = getCurrentLogFile();
            File firstBackup = new File(logDir, LOG_FILE_PREFIX + LOG_FILE_EXTENSION + ".1");
            
            if (currentFile.exists()) {
                if (!currentFile.renameTo(firstBackup)) {
                    Log.w("FileLoggingTree", "Failed to rename current log file to backup");
                }
            }
            
        } catch (Exception e) {
            Log.e("FileLoggingTree", "Error during log file rotation", e);
        }
    }

    /**
     * 현재 로그 파일의 경로를 반환
     */
    public String getCurrentLogFilePath() {
        return getCurrentLogFile().getAbsolutePath();
    }

    /**
     * 로그 디렉토리의 경로를 반환
     */
    public String getLogDirectoryPath() {
        return logDir.getAbsolutePath();
    }

    /**
     * 로그 디렉토리의 모든 로그 파일 목록을 반환
     */
    public File[] getLogFiles() {
        return logDir.listFiles((dir, name) -> name.startsWith(LOG_FILE_PREFIX) && name.endsWith(LOG_FILE_EXTENSION));
    }

    /**
     * 모든 로그 파일을 삭제
     */
    public void clearAllLogs() {
        try {
            File[] logFiles = getLogFiles();
            if (logFiles != null) {
                for (File file : logFiles) {
                    if (!file.delete()) {
                        Log.w("FileLoggingTree", "Failed to delete log file: " + file.getName());
                    }
                }
            }
        } catch (Exception e) {
            Log.e("FileLoggingTree", "Error clearing log files", e);
        }
    }

    @Override
    protected @Nullable String createStackElementTag(@NotNull StackTraceElement element) {
        // 일관된 태그 사용
        return "AIONAD-ADDON";
    }
}

