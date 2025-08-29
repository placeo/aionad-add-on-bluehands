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
import com.skt.aionad.addon.utils.ConfigManager;
import com.skt.aionad.addon.bluehands.CarRepairInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;


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

    private WebView repairStatusWebView;

    private ArrayList<CarRepairInfo> carRepairInfoJobList = new ArrayList<>();
    private ArrayList<CarRepairInfo> carRepairInfoDisplayList = new ArrayList<>();
    

    private final Handler periodicUpdateHandler = new Handler(Looper.getMainLooper());
    private final Runnable periodicUpdateRunnable = new Runnable() {
        @Override public void run() {
            Timber.i("Periodic update: refreshing car repair status.");
            // YKK_TEST data refresh simulation
            carRepairInfoDisplayList.clear(); 
            addCarRepairInfoForTest();

            if (repairStatusWebView != null) {
                updateRepairStatusWebView();
            }

            // 다음 업데이트를 예약합니다.
            periodicUpdateHandler.postDelayed(this, ConfigManager.getInstance().getCarRepairInfoDisplayInterval());
        }
    };

    private void updateRepairStatusWebView() {
        if (repairStatusWebView == null || carRepairInfoDisplayList.isEmpty()) {
            return;
        }

        StringBuilder jsBuilder = new StringBuilder();
        jsBuilder.append("(function(){try{var t=document.querySelector('table');if(!t)return;var r=t.rows;");
        jsBuilder.append("if(r.length>=3){");

        // 최대 4개의 컬럼까지 처리 (현재 HTML 테이블 구조에 맞춤)
        int maxColumns = Math.min(carRepairInfoDisplayList.size(), 4);
        
        for (int i = 0; i < 4; i++) { // 항상 4개 열을 모두 처리
            if (i < carRepairInfoDisplayList.size()) {
                // 데이터가 있는 경우
                CarRepairInfo carInfo = carRepairInfoDisplayList.get(i);
                
                // 상태에 따른 CSS 클래스 결정
                String statusClass = getStatusClass(carInfo.getRepairStatus());
                String statusText = getStatusText(carInfo.getRepairStatus());
                
                // 헤더 업데이트 (첫 번째 행)
                jsBuilder.append(String.format("var h%d=r[0].cells[%d]; h%d.textContent='%s'; h%d.className='h %s';", 
                        i, i, i, statusText, i, statusClass));
                
                // 차량 정보 업데이트 (두 번째 행)
                String plateAndModel = carInfo.getLicensePlateNumber() + " " + carInfo.getCarModel();
                jsBuilder.append(String.format("var p%d=r[1].cells[%d]; p%d.textContent='%s'; p%d.className='plate';", 
                        i, i, i, plateAndModel, i));
                
                // 상태 정보 업데이트 (세 번째 행)
                String statusInfo = getStatusInfoText(carInfo);
                jsBuilder.append(String.format("var s%d=r[2].cells[%d]; s%d.innerHTML='%s'; s%d.className='status';", 
                        i, i, i, statusInfo, i));
            } else {
                // 데이터가 없는 경우 - 빈 열로 설정
                jsBuilder.append(String.format("var h%d=r[0].cells[%d]; h%d.textContent=''; h%d.className='h empty';", 
                        i, i, i, i));
                jsBuilder.append(String.format("var p%d=r[1].cells[%d]; p%d.textContent=''; p%d.className='empty';", 
                        i, i, i, i));
                jsBuilder.append(String.format("var s%d=r[2].cells[%d]; s%d.innerHTML=''; s%d.className='empty';", 
                        i, i, i, i));
            }
        }
        
        jsBuilder.append("}}catch(e){console.error(e);}})();");
        
        String js = jsBuilder.toString();
        repairStatusWebView.evaluateJavascript(js, null);
    }

    private String getStatusClass(CarRepairInfo.RepairStatus status) {
        switch (status) {
            case COMPLETED:
                return "done";
            case FINAL_INSPECTION:
                return "inspect";
            case IN_PROGRESS:
                return "working";
            default:
                return "working";
        }
    }

    private String getStatusText(CarRepairInfo.RepairStatus status) {
        switch (status) {
            case COMPLETED:
                return "작업완료";
            case FINAL_INSPECTION:
                return "최종점검";
            case IN_PROGRESS:
                return "작업중";
            default:
                return "작업중";
        }
    }

    private String getStatusInfoText(CarRepairInfo carInfo) {
        if (carInfo.getRepairStatus() == CarRepairInfo.RepairStatus.COMPLETED) {
            return "완료";
        } else if (carInfo.getEstimatedFinishTimeMinutes() != null) {
            String timeStr = CarRepairInfo.formatMinutesToTime(carInfo.getEstimatedFinishTimeMinutes());
            return "예상 완료 시간 : <span class=\\\"time\\\">" + timeStr + "</span>";
        } else {
            return "시간 미정";
        }
    }

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
        // 액티비티가 보이지 않을 때 주기적인 업데이트를 중지합니다.
        periodicUpdateHandler.removeCallbacks(periodicUpdateRunnable);
        Timber.d("onPause called");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 액티비티가 보일 때 4초 후부터 주기적인 업데이트를 시작합니다.
        // post()는 즉시 실행, postDelayed()는 지정된 시간 후에 실행합니다.
        periodicUpdateHandler.postDelayed(periodicUpdateRunnable, ConfigManager.getInstance().getCarRepairInfoDisplayInterval());
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
        repairStatusWebView = findViewById(R.id.car_repair_status_webview);
        repairStatusWebView.setBackgroundColor(Color.TRANSPARENT);
        repairStatusWebView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        // Enable JavaScript and load status board HTML
        repairStatusWebView.getSettings().setJavaScriptEnabled(true);
        repairStatusWebView.loadUrl("file:///android_asset/bluehands/status_board.html");

        // repair_status_webview.loadDataWithBaseURL(null, tableHtml, "text/html", "UTF-8", null);

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

        periodicUpdateHandler.removeCallbacks(periodicUpdateRunnable);
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

    public void addCarRepairInfoForTest() {
        carRepairInfoDisplayList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.IN_PROGRESS, "2나2", "아반떼MD", 12 * 60));
        carRepairInfoDisplayList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.FINAL_INSPECTION, "3다3", "I520", 13 * 60 + 30));
        carRepairInfoDisplayList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.COMPLETED, "4라4", "모닝", null));
        // carRepairInfoDisplayList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.IN_PROGRESS, "5마5", "K3", 15 * 60 + 30));
    }
}
