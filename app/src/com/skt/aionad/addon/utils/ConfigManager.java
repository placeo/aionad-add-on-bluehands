package com.skt.aionad.addon.utils;

import android.content.Context;
import android.content.res.AssetManager;


import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import timber.log.Timber;

public class ConfigManager {
    private static final String CONFIG_FILE_NAME = "aionad-add-on.config";
    private static ConfigManager sInstance; // Singleton instance
    private JSONObject mConfig;

    // Default values
    private String mLogType = "file"; // "file" or "console"
    private long mLogMaxFileSize = 5 * 1024 * 1024; // 5 MB
    private int mLogMaxBackupIndex = 3;
    private String mLogFrontendLevel = "DEBUG"; // "VERBOSE", "DEBUG", "INFO", "WARN", "ERROR"
    private boolean mMonitorEnabled = true;
    private int mMonitorInterval = 2000; // in milliseconds
    private boolean mFullScreenEnabled = false;
    private long mCarRepairInfoInterval = 4000; // in milliseconds          

    public void loadConfig(Context context) {
        AssetManager assetManager = context.getAssets();
        try (InputStream is = assetManager.open(CONFIG_FILE_NAME);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                int commentIndex = line.indexOf('#');
                if (commentIndex != -1) {
                    line = line.substring(0, commentIndex);
                }
                jsonBuilder.append(line);
            }

            String json = jsonBuilder.toString();
            if (json.trim().isEmpty()) {
                Timber.w("Config file is empty or contains only comments. Using default values.");
                mConfig = new JSONObject(); // Ensure mConfig is not null
                return;
            }

            mConfig = new JSONObject(json);
            parseConfigValues();

        } catch (IOException e) {
            Timber.e(e, "Could not find or open config file '%s' in assets. Using default values.", CONFIG_FILE_NAME);
            mConfig = new JSONObject(); // Ensure mConfig is not null
        } catch (Exception e) {
            Timber.e(e, "Failed to initialize from config, using default values.");
            mConfig = new JSONObject(); // Ensure mConfig is not null
        }
    }

    private void parseConfigValues() {
        // log
        if (mConfig.has("log")) {
            JSONObject logConfig = mConfig.optJSONObject("log");
            if (logConfig != null) {
                mLogType = logConfig.optString("type", mLogType);
                mLogMaxFileSize = logConfig.optLong("maxFileSize", mLogMaxFileSize);
                mLogMaxBackupIndex = logConfig.optInt("maxBackupIndex", mLogMaxBackupIndex);
                if (logConfig.has("frontend")) {
                    JSONObject frontendConfig = logConfig.optJSONObject("frontend");
                    if (frontendConfig != null) {
                        mLogFrontendLevel = frontendConfig.optString("level", mLogFrontendLevel);
                    }
                }
            }
        }

        // monitor
        if (mConfig.has("monitor")) {
            JSONObject monitorConfig = mConfig.optJSONObject("monitor");
            if (monitorConfig != null) {
                mMonitorEnabled = monitorConfig.optBoolean("enable", mMonitorEnabled);
                mMonitorInterval = monitorConfig.optInt("interval", mMonitorInterval);
            }
        }

        // fullScreen
        if (mConfig.has("fullScreen")) {
            JSONObject fullScreenConfig = mConfig.optJSONObject("fullScreen");
            if (fullScreenConfig != null) {
                mFullScreenEnabled = fullScreenConfig.optBoolean("enable", mFullScreenEnabled);
            }
        }
        // carRepairInfo
        if (mConfig.has("carRepairInfo")) {
            JSONObject carRepairInfoConfig = mConfig.optJSONObject("carRepairInfo");
            if (carRepairInfoConfig != null && carRepairInfoConfig.has("display")) {
                JSONObject displayConfig = carRepairInfoConfig.optJSONObject("display");
                if (displayConfig != null) {
                    mCarRepairInfoInterval = displayConfig.optLong("interval", mCarRepairInfoInterval);
                }
            }
        }
    }

    public String getLogType() {
        return mLogType;
    }

    public long getLogMaxFileSize() {
        return mLogMaxFileSize;
    }
    public int getLogMaxBackupIndex() {
        return mLogMaxBackupIndex;
    }   

    public String getLogFrontendLevel() {
        return mLogFrontendLevel;
    }

    public boolean isMonitorEnabled() {
        return mMonitorEnabled;
    }

    public int getMonitorInterval() {
        return mMonitorInterval;
    }

    public boolean isFullScreenEnabled() {
        return mFullScreenEnabled;
    }

    public long getCarRepairInfoDisplayInterval() {
        return mCarRepairInfoInterval;
    }

    public static ConfigManager getInstance() {
        if (sInstance == null) {
            synchronized (ConfigManager.class) {
                if (sInstance == null) {
                    sInstance = new ConfigManager();
                }
            }
        }
        return sInstance;
    }

    private ConfigManager() {
        // Private constructor for singleton
    }
}
