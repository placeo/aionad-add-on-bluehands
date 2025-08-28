package com.skt.aionad.addon;

import android.graphics.Point;
import android.graphics.Rect;
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
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
import android.view.WindowMetrics;
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

        // Get and log screen resolution
        Point resolution = getScreenResolution();
        Timber.i("Screen Resolution: %d x %d", resolution.x, resolution.y);

        // Keep screen on to prevent screensaver/sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main);

        // Start Ktor Server Service
        Intent serverIntent = new Intent(this, KtorServerService.class);
        startService(serverIntent);

        // Set WebView background transparent and prepare 4x3 grid (4th column empty)
        WebView repair_status_webview = findViewById(R.id.car_repair_status_webview);
        repair_status_webview.setBackgroundColor(Color.TRANSPARENT);
        repair_status_webview.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        // Enable JavaScript and load status board HTML
        repair_status_webview.getSettings().setJavaScriptEnabled(true);
        String tableHtml = "<html>" +
                "<head>" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1\">" +
                "<style>" +
                "html, body { height:100%; margin:0; padding:0; background:transparent; font-family:-apple-system, Roboto, Arial, sans-serif; }" +
                "table { width:100%; height:100%; border-collapse:collapse; table-layout:fixed; }" +
                "th, td { border: 0; padding: 0.4rem; text-align:center; vertical-align:middle; }" +
                ".h { color:#ffffff; font-weight:700; font-size:1.0rem; }" +
                ".done { background:#d9534f; }" +
                ".inspect { background:#6c757d; }" +
                ".working { background:#2f6db3; }" +
                ".empty { background:transparent; }" +
                ".plate { font-weight:700; font-size:1.1rem; color:#0a2540; }" +
                ".status { font-size:0.95rem; color:#0a2540; }" +
                ".time { font-size:1.6rem; font-weight:800; color:#2f6db3; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<table>" +
                // Header row
                "<tr>" +
                "<th class=\"h done\">작업완료</th>" +
                "<th class=\"h inspect\">최종점검</th>" +
                "<th class=\"h working\">작업중</th>" +
                "<th class=\"h empty\"></th>" +
                "</tr>" +
                // Row 2: plates
                "<tr>" +
                "<td class=\"plate\">＊＊러7821 아반떼MD</td>" +
                "<td class=\"plate\">＊＊나5195 G80(DH)</td>" +
                "<td class=\"plate\">＊＊마0179 포터2</td>" +
                "<td class=\"empty\"></td>" +
                "</tr>" +
                // Row 3: status/time
                "<tr>" +
                "<td class=\"status\">완료</td>" +
                "<td class=\"status\">예상 완료 시간 : <span class=\"time\">15:00</span></td>" +
                "<td class=\"status\">예상 완료 시간 : <span class=\"time\">15:30</span></td>" +
                "<td class=\"empty\"></td>" +
                "</tr>" +
                "</table>" +
                "</body>" +
                "</html>";

        repair_status_webview.loadDataWithBaseURL(null, tableHtml, "text/html", "UTF-8", null);

        // 화면 전환을 위한 View 참조 설정
        controlPanel = findViewById(R.id.control_panel);
        
        Timber.i("onCreate end");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        // Stop Ktor Server Service
        Intent serverIntent = new Intent(this, KtorServerService.class);
        stopService(serverIntent);
        Timber.d("onDestroy called, stopping KtorServerService");

        super.onDestroy();
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

    @SuppressWarnings("deprecation")
    private Point getScreenResolution() {
        Point size = new Point();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = getWindowManager().getMaximumWindowMetrics();
            Rect bounds = windowMetrics.getBounds();
            size.x = bounds.width();
            size.y = bounds.height();
        } else {
            android.view.Display display = getWindowManager().getDefaultDisplay();
            display.getRealSize(size);
        }
        return size;
    }

}
