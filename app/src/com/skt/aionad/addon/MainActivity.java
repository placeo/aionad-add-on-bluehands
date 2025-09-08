package com.skt.aionad.addon;

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
import android.content.Context;
import android.content.Intent;
import com.skt.aionad.addon.server.KtorServerService;
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
    private static final long FULLSCREEN_TOGGLE_DELAY_MS = 10000;

    private SurfaceHolder surfaceHolder;
    private PowerManager.WakeLock mWakeLock;  // WakeLock 변수 선언
    
    // 화면 전환을 위한 변수들
    private Handler toggleHandler;
    private Runnable toggleRunnable;
    private boolean isFullScreenMode = false;
    private boolean isTimerRunning = false;
    private View controlPanel;
    private View videoContainer;

    // AddOnBluehands 인스턴스
    private AddOnBluehands addOnBluehands;

    @Override
    protected void onStart() {
        super.onStart();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIOnAdAddOn::WakeLock");
            mWakeLock.acquire();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();

        Timber.d("onPause called");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
  
        Timber.d("onResume called");
    }
    
    // Called when the activity is first created.
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Timber.i("onCreate called");
        super.onCreate(savedInstanceState);

        // Keep screen on to prevent screensaver/sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main);

        // Start Ktor Server Service
        Intent serverIntent = new Intent(this, KtorServerService.class);
        startService(serverIntent);

        // 화면 전환을 위한 View 참조 설정
        controlPanel = findViewById(R.id.control_panel);

        // AddOnBluehands 초기화
        initializeAddOnBluehands();
        
        // AddOnBluehands의 주기적 업데이트 시작
        if (addOnBluehands != null) {
            addOnBluehands.startPeriodicUpdates();
        }

        Timber.i("onCreate end");
    }

    private void initializeAddOnBluehands() {
        addOnBluehands = new AddOnBluehands(this);
        
        // UI 요소들을 찾아서 AddOnBluehands에 전달
        WebView repairStatusWebView = findViewById(R.id.car_repair_status_webview);
        WebView videoWebView = findViewById(R.id.video_webview);
        TextView statusSummaryText = findViewById(R.id.status_summary_text);
        TextView carRepairStatusInfoText = findViewById(R.id.car_repair_status_info_text);
        
        // AddOnBluehands 초기화
        addOnBluehands.initialize(repairStatusWebView, videoWebView, statusSummaryText, carRepairStatusInfoText);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        // AddOnBluehands의 주기적 업데이트 중지
        if (addOnBluehands != null) {
            addOnBluehands.stopPeriodicUpdates();
        }

        // Stop Ktor Server Service
        Intent serverIntent = new Intent(this, KtorServerService.class);
        stopService(serverIntent);
        Timber.d("onDestroy called, stopping KtorServerService");

        // AddOnBluehands 리소스 정리
        if (addOnBluehands != null) {
            addOnBluehands.cleanup();
        }

        super.onDestroy();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
        Timber.d("Surface changed to format %d, width: %d, height: %d", format, width, height);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Timber.d("Surface created");
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Timber.d("Surface destroyed");
    }

    // AddOnBluehands에 대한 공개 접근자 메서드들 (필요한 경우)
    public AddOnBluehands getAddOnBluehands() {
        return addOnBluehands;
    }
}