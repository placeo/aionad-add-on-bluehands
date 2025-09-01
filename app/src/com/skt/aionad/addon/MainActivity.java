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
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CopyOnWriteArrayList;
import android.webkit.WebView;
import android.webkit.WebChromeClient;


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
    private TextView statusSummaryText;

    // 스레드 안전한 리스트 - 외부에서 수시로 추가될 수 있음
    private CopyOnWriteArrayList<CarRepairInfo> carRepairInfoJobList = new CopyOnWriteArrayList<>();
    // 내부 정렬용 리스트 - 메인 스레드에서만 접근
    private ArrayList<CarRepairInfo> carRepairInfoFinishTimeSortedList = new ArrayList<>();
    // 화면 표시용 리스트 - 메인 스레드에서만 접근
    private ArrayList<CarRepairInfo> carRepairInfoDisplayList = new ArrayList<>();
    
    // 페이지네이션을 위한 변수들
    private int currentPageIndex = 0;
    private static final int ITEMS_PER_PAGE = 4;
    

    private final Handler periodicUpdateHandler = new Handler(Looper.getMainLooper());
    private final Runnable periodicUpdateRunnable = new Runnable() {
        @Override public void run() {
            Timber.i("Periodic update: Page %d", currentPageIndex);
            
            // 새로운 사이클 시작 시에만 데이터를 새로 로드하고 정렬
            if (currentPageIndex == 0) {
                // YKK_TEST data refresh simulation (실제로는 서버에서 데이터를 받아올 것)
                addCarRepairInfoForTest();
                sortCarRepairInfoByFinishTime();
                Timber.i("New cycle started: JobList refreshed and sorted");
            }
            
            // 현재 페이지의 아이템들을 DisplayList에 설정
            updateDisplayListForCurrentPage();

            // 화면에 표시
            if (repairStatusWebView != null) {
                updateRepairStatusWebView();
            }

            // 다음 페이지 준비
            moveToNextPageOrRestart();

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

        // Initialize status summary TextView
        statusSummaryText = findViewById(R.id.status_summary_text);

        // Enable JavaScript and load status board HTML
        repairStatusWebView.getSettings().setJavaScriptEnabled(true);

        // Set WebChromeClient to detect title changes
        repairStatusWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (title.contains("작업완료:") && statusSummaryText != null) {
                    runOnUiThread(() -> statusSummaryText.setText(title));
                }
            }
        });

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
        // 테스트를 위해 carRepairInfoJobList에 더 많은 데이터 추가
        carRepairInfoJobList.clear();
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.IN_PROGRESS, "1가1", "소나타", 10 * 60));
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.IN_PROGRESS, "2나2", "아반떼MD", 12 * 60));
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.FINAL_INSPECTION, "3다3", "I520", 13 * 60 + 30));
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.COMPLETED, "4라4", "모닝", null));
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.IN_PROGRESS, "5마5", "K3", 15 * 60 + 30));
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.IN_PROGRESS, "6바6", "투싼", 9 * 60 + 45));
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.FINAL_INSPECTION, "7사7", "그랜저", 11 * 60 + 20));
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.IN_PROGRESS, "8아8", "스파크", 14 * 60));
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.IN_PROGRESS, "9자9", "레이", 15 * 60 + 30));
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.COMPLETED, "10차10", "레이스", null));
        // 10개의 테스트 데이터로 페이지네이션 테스트 가능
    }

    /**
     * carRepairInfoJobList를 완료시간 기준으로 정렬하여 carRepairInfoFinishTimeSortedList에 저장
     */
    private void sortCarRepairInfoByFinishTime() {
        carRepairInfoFinishTimeSortedList.clear();
        carRepairInfoFinishTimeSortedList.addAll(carRepairInfoJobList);
        
        Collections.sort(carRepairInfoFinishTimeSortedList, new Comparator<CarRepairInfo>() {
            @Override
            public int compare(CarRepairInfo o1, CarRepairInfo o2) {
                // 완료된 작업은 맨 앞으로
                if (o1.getRepairStatus() == CarRepairInfo.RepairStatus.COMPLETED && 
                    o2.getRepairStatus() != CarRepairInfo.RepairStatus.COMPLETED) {
                    return -1;  // o1이 완료된 경우 앞으로
                }
                if (o2.getRepairStatus() == CarRepairInfo.RepairStatus.COMPLETED && 
                    o1.getRepairStatus() != CarRepairInfo.RepairStatus.COMPLETED) {
                    return 1;   // o2가 완료된 경우 o2가 앞으로
                }
                
                // 둘 다 완료된 경우 또는 둘 다 진행 중인 경우
                if (o1.getRepairStatus() == CarRepairInfo.RepairStatus.COMPLETED && 
                    o2.getRepairStatus() == CarRepairInfo.RepairStatus.COMPLETED) {
                    // 완료된 작업들끼리는 차량번호 순으로 정렬
                    return o1.getLicensePlateNumber().compareTo(o2.getLicensePlateNumber());
                }
                
                // 둘 다 진행 중인 경우: 완료시간 기준 정렬 (null은 맨 뒤로)
                if (o1.getEstimatedFinishTimeMinutes() == null && o2.getEstimatedFinishTimeMinutes() == null) {
                    return 0;
                }
                if (o1.getEstimatedFinishTimeMinutes() == null) {
                    return 1;
                }
                if (o2.getEstimatedFinishTimeMinutes() == null) {
                    return -1;
                }
                
                return o1.getEstimatedFinishTimeMinutes().compareTo(o2.getEstimatedFinishTimeMinutes());
            }
        });
        
        Timber.i("Sorted repair info list. Total items: %d", carRepairInfoFinishTimeSortedList.size());
        
        // 정렬 결과 디버그 로그
        for (int i = 0; i < carRepairInfoFinishTimeSortedList.size(); i++) {
            CarRepairInfo info = carRepairInfoFinishTimeSortedList.get(i);
            Timber.d("Sorted[%d]: %s %s - %s (Time: %s)", 
                i, 
                info.getLicensePlateNumber(), 
                info.getCarModel(), 
                info.getRepairStatus().name(),
                info.getEstimatedFinishTimeMinutes() != null ? 
                    CarRepairInfo.formatMinutesToTime(info.getEstimatedFinishTimeMinutes()) : "null"
            );
        }
    }

    /**
     * currentPageIndex를 기준으로 4개씩 carRepairInfoDisplayList에 설정
     */
    private void updateDisplayListForCurrentPage() {
        carRepairInfoDisplayList.clear();
        
        int startIndex = currentPageIndex * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, carRepairInfoFinishTimeSortedList.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            carRepairInfoDisplayList.add(carRepairInfoFinishTimeSortedList.get(i));
        }
        
        Timber.i("Updated display list for page %d. Items %d-%d (total: %d)", 
            currentPageIndex, startIndex, endIndex - 1, carRepairInfoDisplayList.size());
    }

    /**
     * 다음 페이지로 이동하거나 처음부터 다시 시작
     */
    private void moveToNextPageOrRestart() {
        currentPageIndex++;
        int totalPages = (int) Math.ceil((double) carRepairInfoFinishTimeSortedList.size() / ITEMS_PER_PAGE);
        
        if (currentPageIndex >= totalPages) {
            // 모든 페이지를 다 보여줬으므로 처음부터 다시 시작
            currentPageIndex = 0;
            sortCarRepairInfoByFinishTime(); // carRepairInfoJobList로부터 다시 정렬
            Timber.i("All pages shown, restarting cycle with fresh sort. Total pages: %d", totalPages);
        } else {
            Timber.i("Moving to next page: %d/%d", currentPageIndex + 1, totalPages);
        }
    }

    /**
     * 스레드 안전하게 새로운 수리 정보를 추가
     * 외부 스레드(예: Ktor 서버)에서 호출 가능
     */
    public void addCarRepairInfo(CarRepairInfo carRepairInfo) {
        if (carRepairInfo != null) {
            carRepairInfoJobList.add(carRepairInfo);
            Timber.i("Added new repair info: %s %s (Thread: %s)", 
                carRepairInfo.getLicensePlateNumber(), 
                carRepairInfo.getCarModel(),
                Thread.currentThread().getName());
        }
    }

    /**
     * 스레드 안전하게 수리 정보를 제거
     * 외부 스레드에서 호출 가능
     */
    public boolean removeCarRepairInfo(String licensePlateNumber) {
        boolean removed = carRepairInfoJobList.removeIf(info -> 
            info.getLicensePlateNumber().equals(licensePlateNumber));
        
        if (removed) {
            Timber.i("Removed repair info: %s (Thread: %s)", 
                licensePlateNumber, Thread.currentThread().getName());
        }
        return removed;
    }

    /**
     * 스레드 안전하게 수리 정보를 업데이트 (상태나 완료시간 변경)
     * 외부 스레드에서 호출 가능
     */
    public boolean updateCarRepairInfo(String licensePlateNumber, CarRepairInfo.RepairStatus newStatus, Integer newFinishTime) {
        for (CarRepairInfo info : carRepairInfoJobList) {
            if (info.getLicensePlateNumber().equals(licensePlateNumber)) {
                // CarRepairInfo가 immutable하다면 새 객체로 교체해야 함
                // 현재는 setter가 있다고 가정
                if (newStatus != null) {
                    info.setRepairStatus(newStatus);
                }
                if (newFinishTime != null) {
                    info.setEstimatedFinishTimeMinutes(newFinishTime);
                }
                
                Timber.i("Updated repair info: %s, Status: %s, Time: %d (Thread: %s)", 
                    licensePlateNumber, newStatus, newFinishTime, Thread.currentThread().getName());
                return true;
            }
        }
        return false;
    }
}
