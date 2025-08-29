/*
 * Copyright 2017-2022 Jiangdg
 *
 * ... (license header)
 */
package com.skt.aionad.addon;

import android.content.Context;
import android.app.Application;
import timber.log.Timber;

import com.skt.aionad.addon.utils.ColorLogTree;
import com.skt.aionad.addon.utils.ConfigManager;
import com.skt.aionad.addon.utils.FileLoggingTree;

/**
 *
 * Created by jiangdg on 2022/2/28
 */
public class DemoApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Load configuration first to decide how to set up logging.
        ConfigManager config = ConfigManager.getInstance();
        config.loadConfig(this);

        // 2. Initialize Timber based on the loaded configuration.
        initializeLogging(this, config);

        // 3. Now, we can start logging. The logs will respect the config.
        Timber.i("--- Loaded Configuration ---");
        Timber.i("Log Type: %s", config.getLogType());
        Timber.i("Log Max File Size: %d", config.getLogMaxFileSize());
        Timber.i("Log Max Backup Index: %d", config.getLogMaxBackupIndex());
        Timber.i("Log Frontend Level: %s", config.getLogFrontendLevel());
        Timber.i("Monitor Enabled: %b", config.isMonitorEnabled());
        Timber.i("Monitor Interval: %d", config.getMonitorInterval());
        Timber.i("FullScreen enabled: %b", config.isFullScreenEnabled());
        Timber.i("Car Repair Info Display Interval: %dms", config.getCarRepairInfoDisplayInterval());
        Timber.i("--------------------------");
    }

    private void initializeLogging(Context context, ConfigManager config) {
        // Un-plant all previous trees to avoid duplicates on re-initialization.
        Timber.uprootAll();

        String logType = config.getLogType().toLowerCase();
        String logLevel = config.getLogFrontendLevel();

        // Plant console logger if type is "console" or "both".
        if (logType.equals("console") || logType.equals("both")) {
            Timber.plant(new ColorLogTree(logLevel));
        }

        // Plant file logger if type is "file" or "both".
        if (logType.equals("file") || logType.equals("both")) {
            // Note: This is a basic file logger. For a production app, you might want a more robust solution.
            Timber.plant(new FileLoggingTree(context, logLevel));
        }
    }
}
