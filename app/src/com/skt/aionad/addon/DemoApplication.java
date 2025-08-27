/*
 * Copyright 2017-2022 Jiangdg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.skt.aionad.addon;

import android.app.Application;
import timber.log.Timber;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

import android.media.MediaCodecList;
import android.media.MediaCodecInfo;

import com.skt.aionad.addon.utils.ColorLogTree;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 *
 * Created by jiangdg on 2022/2/28
 */
public class DemoApplication extends Application {

    // Load native libraries
    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("AionadAddOnBluehands");
        com.skt.aionad.addon.MainActivity.nativeClassInit();
    }

    private static int sResolutionWidth = 1280;
    private static int sResolutionHeight = 720;
    private static volatile int sFps = 30;
    private static volatile boolean sFullScreenEnabled = false;

    public static int getGlobalResolutionWidth() { return sResolutionWidth; }
    public static int getGlobalResolutionHeight() { return sResolutionHeight; }
    public static int getGlobalFps() { return sFps; }
    public static boolean isGlobalFullScreenEnabled() { return sFullScreenEnabled; }

    @Override
    public void onCreate() {
        super.onCreate();

        Timber.plant(new ColorLogTree());

        copyAssetToFile("aionad-add-on.config");
        initializeFpsFromConfig();
        initializeFullScreenFromConfig();

        Timber.i("Resolution width: %d, height: %d", sResolutionWidth, sResolutionHeight);
        Timber.i("Configured FPS: %d", sFps);
        Timber.i("FullScreen enabled: %b", sFullScreenEnabled);

        Timber.i("[YKK_TEST] Codec Check");
        CodecCheck();
    }

    private void initializeFpsFromConfig() {
        try {
            File configFile = new File(getFilesDir(), "aionad-add-on.config");
            if (!configFile.exists()) {
                Timber.e("Config file not found, skipping fps initialization.");
                return;
            }

            StringBuilder jsonBuilder = new StringBuilder();
            try (InputStream is = new FileInputStream(configFile);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int commentIndex = line.indexOf('#');
                    if (commentIndex != -1) {
                        line = line.substring(0, commentIndex);
                    }
                    jsonBuilder.append(line);
                }
            }

            String json = jsonBuilder.toString();
            if (json.trim().isEmpty()) {
                Timber.w("Config file is empty or contains only comments. Using default FPS: %d", sFps);
                return;
            }

            JSONObject config = new JSONObject(json);
            sFps = config.optInt("fps", sFps);
        } catch (Exception e) {
            Timber.e(e, "Failed to initialize fps from config", e);
        }
    }

    private void initializeFullScreenFromConfig() {
        try {
            File configFile = new File(getFilesDir(), "aionad-add-on.config");
            if (!configFile.exists()) {
                Timber.e("Config file not found, skipping fullScreen initialization.");
                return;
            }

            StringBuilder jsonBuilder = new StringBuilder();
            try (InputStream is = new FileInputStream(configFile);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int commentIndex = line.indexOf('#');
                    if (commentIndex != -1) {
                        line = line.substring(0, commentIndex);
                    }
                    jsonBuilder.append(line);
                }
            }

            String json = jsonBuilder.toString();
            if (json.trim().isEmpty()) {
                Timber.w("Config file is empty or contains only comments. Using default fullScreen: %b", sFullScreenEnabled);
                return;
            }

            JSONObject config = new JSONObject(json);
            if (config.has("fullScreen")) {
                JSONObject fullScreenConfig = config.getJSONObject("fullScreen");
                sFullScreenEnabled = fullScreenConfig.optBoolean("enable", sFullScreenEnabled);
            }
        } catch (Exception e) {
            Timber.e(e, "Failed to initialize fullScreen from config", e);
        }
    }

    private void copyAssetToFile(String filename) {
        File outFile = new File(getFilesDir(), filename);

        AssetManager assetManager = getAssets();
        try (InputStream in = assetManager.open(filename);
            OutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            e.printStackTrace(); // Log error
        }
        Timber.i("%s conf is copied to %s", filename, outFile.getAbsolutePath());
    }

    private void CodecCheck() {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            String name = codecInfo.getName().toLowerCase();
            // Heuristic check for decoders, as isDecoder() might cause issues.
            if (!name.contains("decoder") && !name.contains("dec")) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.startsWith("video/")) {
                    boolean isHardware = false;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        isHardware = codecInfo.isHardwareAccelerated();
                    } else {
                        // Heuristic for older APIs
                        if (name.contains("omx.") && !name.contains(".sw.")) {
                            isHardware = true;
                        }
                    }

                    if (isHardware) {
                        Timber.i("Found HW video decoder: %s for type %s", codecInfo.getName(), type);
                    } else {
                        Timber.i("Found SW video decoder: %s for type %s", codecInfo.getName(), type);
                    }
                }
            }
        }
    }
}
