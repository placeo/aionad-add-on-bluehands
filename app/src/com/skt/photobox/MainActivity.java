package com.skt.photobox;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PowerManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebView;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;

import androidx.fragment.app.Fragment;

import androidx.core.content.PermissionChecker;
import org.freedesktop.gstreamer.GStreamer;

import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

import com.jiangdg.ausbc.utils.Utils;
import com.jiangdg.ausbc.utils.bus.BusKey;
import com.jiangdg.ausbc.utils.bus.EventBus;

import android.content.Context;
import android.content.Intent;
import com.skt.photobox.server.KtorServerService;
import timber.log.Timber;
import androidx.appcompat.app.AppCompatActivity;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import androidx.constraintlayout.widget.ConstraintLayout;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;


public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;

    private SurfaceHolder surfaceHolder;
    private PowerManager.WakeLock mWakeLock;  // WakeLock 변수 선언
    
    // 화면 전환을 위한 변수들
    private Handler toggleHandler;
    private Runnable toggleRunnable;
    private boolean isFullScreenMode = false;
    private boolean isTimerRunning = false;
    private View controlPanel;
    private View videoContainer;
    private native void nativeInit();     // Initialize native code, build pipeline, etc
    private native void nativeFinalize(); // Destroy pipeline and shutdown native code
    private native void nativePlay();     // Set pipeline to PLAYING
    private native void nativePause();    // Set pipeline to PAUSED
    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
    private native void nativeSurfaceInit(Object surface);
    private native void nativeSurfaceFinalize();
    

    private long native_custom_data;      // Native code will use this to keep private data

    private boolean is_playing_desired;   // Whether the user asked to go to PLAYING

    @Override
    protected void onStart() {
        super.onStart();
        mWakeLock = Utils.INSTANCE.wakeLock(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mWakeLock != null) {
            Utils.INSTANCE.wakeUnLock(mWakeLock);
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // 앱이 백그라운드로 갈 때 타이머 정지
        stopToggleTimer();
        Timber.d("onPause - timer stopped");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // onPause에서 정지된 타이머를 재시작 (fullScreen 설정이 활성화된 경우에만)
        if (!isTimerRunning && DemoApplication.isGlobalFullScreenEnabled()) {
            startToggleTimer();
            Timber.d("onResume - timer restarted");
        } else if (!DemoApplication.isGlobalFullScreenEnabled()) {
            Timber.d("onResume - fullScreen disabled, skip timer restart");
        } else {
            Timber.d("onResume - timer already running, skip restart");
        }
    }
    
    // Called when the activity is first created.
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Timber.i("%s, %s called", new Throwable().getStackTrace()[0].getClassName(), new Throwable().getStackTrace()[0].getMethodName());
        super.onCreate(savedInstanceState);

        // Keep screen on to prevent screensaver/sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Initialize GStreamer and warn if it fails
        try {
            GStreamer.init(this);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.main);

        // 로고 오버레이 WebView 설정
        WebView logoOverlayWebView = findViewById(R.id.logo_overlay_webview);
        logoOverlayWebView.setClickable(false);
        logoOverlayWebView.setFocusable(false);
        logoOverlayWebView.setFocusableInTouchMode(false);
        logoOverlayWebView.getSettings().setJavaScriptEnabled(true);
        logoOverlayWebView.setBackgroundColor(Color.TRANSPARENT);

        String logoHtmlData = "<html><head>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' />"
                + "<style>"
                + "body { margin: 0; overflow: hidden; background-color: transparent; }"
                + ".ticker-container { width: 100%; height: 100%; display: flex; align-items: center; overflow: hidden; }"
                + ".ticker-track { display: flex; flex-shrink: 0; white-space: nowrap; animation: continuous-ticker 40s linear infinite; }"
                + "img { margin: 0 15px; /* 이미지 사이 간격 */ }"
                + "@keyframes continuous-ticker { from { transform: translateX(0); } to { transform: translateX(-50%); } }"
                + "</style></head><body>"
                + "<div class='ticker-container'>"
                + "<div class='ticker-track'>"
                // 그룹 1
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                // 그룹 2 (끊김 없는 애니메이션을 위해)
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "<img src='file:///android_res/mipmap/oliveyoung_small_logo.png' />"
                + "</div>"
                + "</div></body></html>";
        logoOverlayWebView.loadDataWithBaseURL("file:///android_res/", logoHtmlData, "text/html", "UTF-8", null);
        logoOverlayWebView.setVisibility(View.VISIBLE);
/* 
        ImageButton play = (ImageButton) this.findViewById(R.id.button_play);
        play.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                is_playing_desired = true;
                nativePlay();
            }
        });

        ImageButton pause = (ImageButton) this.findViewById(R.id.button_stop);
        pause.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                is_playing_desired = false;
                nativePause();
            }
        });
*/
        SurfaceView sv = (SurfaceView) this.findViewById(R.id.surface_video);
        SurfaceHolder sh = sv.getHolder();
        this.surfaceHolder = sh; // SurfaceHolder 저장
        sh.addCallback(this);

        if (savedInstanceState != null) {
            is_playing_desired = savedInstanceState.getBoolean("playing");
            Timber.i("Activity created. Saved state is playing: %b", is_playing_desired);
        } else {
            is_playing_desired = false;
            Timber.i("Activity created. There is no saved state, playing: false");
        }

        // Start with disabled buttons, until native code is initialized
        //this.findViewById(R.id.button_play).setEnabled(false);
        //this.findViewById(R.id.button_stop).setEnabled(false);

        // Start Ktor Server Service
        Intent serverIntent = new Intent(this, KtorServerService.class);
        startService(serverIntent);

        nativeInit();

        int usbDeviceId = getIntent().getIntExtra(KEY_USB_DEVICE, -1);
        replaceDemoFragment(DemoFragment.newInstance(usbDeviceId));
        
        // 화면 전환을 위한 View 참조 설정
        controlPanel = findViewById(R.id.control_panel);
        videoContainer = findViewById(R.id.video_container);
        
        // 10초 간격으로 화면 전환하는 타이머 시작
        startToggleTimer();
        
        Timber.i("%s, %s end", new Throwable().getStackTrace()[0].getClassName(), new Throwable().getStackTrace()[0].getMethodName());
    }

    private void replaceDemoFragment(Fragment fragment) {
        int hasCameraPermission = PermissionChecker.checkSelfPermission(this, Manifest.permission.CAMERA);
        int hasStoragePermission = PermissionChecker.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (hasCameraPermission != PermissionChecker.PERMISSION_GRANTED || hasStoragePermission != PermissionChecker.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                Toast.makeText(this, R.string.permission_tip, Toast.LENGTH_LONG).show();
            }
            ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO},
                REQUEST_CAMERA
            );
            return;
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commitAllowingStateLoss();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("playing", is_playing_desired);
        Timber.d("Saving state, playing: %b", is_playing_desired);
    }

    @Override
    protected void onDestroy() {
        // Stop Ktor Server Service
        Intent serverIntent = new Intent(this, KtorServerService.class);
        stopService(serverIntent);
        
        // 타이머 정리
        stopToggleTimer();

        nativeFinalize();
        super.onDestroy();
    }
    
    /**
     * 10초 간격으로 화면 전환하는 타이머 시작
     */
    private void startToggleTimer() {
        // fullScreen 설정이 비활성화되어 있으면 타이머를 시작하지 않음
        if (!DemoApplication.isGlobalFullScreenEnabled()) {
            Timber.d("FullScreen toggle is disabled in config, skip timer start");
            return;
        }
        
        if (isTimerRunning) {
            Timber.d("Timer already running, skip start");
            return;
        }
        
        toggleHandler = new Handler(Looper.getMainLooper());
        toggleRunnable = new Runnable() {
            @Override
            public void run() {
                toggleScreenMode();
                // 10초 후에 다시 실행
                if (isTimerRunning && toggleHandler != null) {
                    toggleHandler.postDelayed(this, 10000);
                }
            }
        };
        // 처음 10초 후에 시작
        toggleHandler.postDelayed(toggleRunnable, 10000);
        isTimerRunning = true;
        Timber.d("Timer started - first toggle in 10 seconds");
    }
    
    /**
     * 타이머 정지
     */
    private void stopToggleTimer() {
        if (toggleHandler != null && toggleRunnable != null) {
            toggleHandler.removeCallbacks(toggleRunnable);
            toggleHandler = null;
            toggleRunnable = null;
        }
        isTimerRunning = false;
        Timber.d("Timer stopped and cleared");
    }
    
    /**
     * 화면 모드 전환 (일반 화면 ↔ 전체 화면)
     */
    private void toggleScreenMode() {
        if (isFullScreenMode) {
            // 전체 화면 → 일반 화면
            showNormalMode();
        } else {
            // 일반 화면 → 전체 화면
            showFullScreenMode();
        }
        isFullScreenMode = !isFullScreenMode;
        
        Timber.i("Screen mode toggled to: %s", isFullScreenMode ? "FullScreen" : "Normal");
    }
    
    /**
     * 일반 모드 (컨트롤 패널 + 비디오)
     */
    private void showNormalMode() {
        if (controlPanel != null) {
            controlPanel.setVisibility(View.VISIBLE);
        }
        if (videoContainer != null) {
            // 새로운 LayoutParams 생성 (원본 XML과 동일하게)
            ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(0, 0);
            
            // 원본 XML의 제약 조건들을 정확히 복원
            params.topToBottom = R.id.control_panel;
            params.bottomToTop = R.id.guideline_bottom_margin_area;
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            params.verticalBias = 0.5f;
            params.dimensionRatio = "16:9";
            
            videoContainer.setLayoutParams(params);
            videoContainer.requestLayout();
            videoContainer.invalidate();
            Timber.d("Normal mode applied - control panel visible, video container resized");
        }
    }
    
    /**
     * 전체 화면 모드 (비디오만)
     */
    private void showFullScreenMode() {
        if (controlPanel != null) {
            controlPanel.setVisibility(View.GONE);
        }
        if (videoContainer != null) {
            // 새로운 LayoutParams 생성 (전체 화면용)
            ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(0, 0);
            
            // 전체 화면을 위한 제약 조건들
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            
            // 차원 비율은 제거 (전체 화면에서는 불필요)
            params.dimensionRatio = null;
            
            videoContainer.setLayoutParams(params);
            videoContainer.requestLayout();
            videoContainer.invalidate();
            Timber.d("Full screen mode applied - control panel hidden, video container full screen");
        }
    }

    // Called from native code. This sets the content of the TextView from the UI thread.
    private void setMessage(final String message) {
        /* YKK_TEST 20250717 disable textview
        final TextView tv = (TextView) this.findViewById(R.id.textview_message);
        runOnUiThread (new Runnable() {
          public void run() {
            tv.setText(message);
          }
        });
        */
    }

    // Called from native code. Native code calls this once it has created its pipeline and
    // the main loop is running, so it is ready to accept commands.
    private void onGStreamerInitialized () {
        // You can get sinkElement source here when needed.
        Timber.i("Gst initialized. Restoring state, playing: %b", is_playing_desired);
        

        
        // Restore playing state
        if (is_playing_desired) {
            nativePlay();
        } else {
            // YKK_TEST
            // nativePause();
            nativePlay();
        }

        // Re-enable buttons, now that GStreamer is initialized
/* 
        final Activity activity = this;
        runOnUiThread(new Runnable() {
            public void run() {
                activity.findViewById(R.id.button_play).setEnabled(true);
                activity.findViewById(R.id.button_stop).setEnabled(true);
            }
        });
*/
    }


    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("PhotoBox");
        nativeClassInit();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
        Timber.d("Surface changed to format %d, width: %d, height: %d", format, width, height);
        nativeSurfaceInit (holder.getSurface());
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Timber.d("Surface created");
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Timber.d("Surface destroyed");
        nativeSurfaceFinalize ();
    }

    public static Intent newInstance(Context context, int usbDeviceId) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(KEY_USB_DEVICE, usbDeviceId);
        return intent;
    }
    private static final int REQUEST_CAMERA = 0;
    private static final int REQUEST_STORAGE = 1;
    public static final String KEY_USB_DEVICE = "usbDeviceId";
}
