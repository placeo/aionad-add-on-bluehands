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

    private static int sResolutionWidth = 1920;
    private static int sResolutionHeight = 1080;
    private static volatile boolean sFullScreenEnabled = false;

    public static int getGlobalResolutionWidth() { return sResolutionWidth; }
    public static int getGlobalResolutionHeight() { return sResolutionHeight; }
    public static boolean isGlobalFullScreenEnabled() { return sFullScreenEnabled; }

    @Override
    public void onCreate() {
        super.onCreate();

        Timber.plant(new ColorLogTree());

        copyAssetToFile("aionad-add-on.config");
        initializeFullScreenFromConfig();

        Timber.i("Resolution width: %d, height: %d", sResolutionWidth, sResolutionHeight);
        Timber.i("FullScreen enabled: %b", sFullScreenEnabled);
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
}
