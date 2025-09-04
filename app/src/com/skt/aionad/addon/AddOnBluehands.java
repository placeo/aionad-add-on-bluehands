package com.skt.aionad.addon;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import timber.log.Timber;

import com.skt.aionad.addon.bluehands.CarRepairInfo;
import com.skt.aionad.addon.utils.ConfigManager;
import com.skt.aionad.addon.server.KtorServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 차량 수리 상태 관리 및 표시를 담당하는 클래스
 * Bluehands 특화 기능들을 포함
 */
public class AddOnBluehands {
    
    // 테스트 데이터 초기화 카운터 (static 변수)
    private static int testDataInitCount = 0;

    private final Context context;
    private WebView repairStatusWebView;
    private WebView videoWebView;
    private TextView statusSummaryText;
    private TextView carRepairStatusInfoText;
    private KtorServer ktorServer;

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
    private long lastUpdateTime = 0; // 마지막 업데이트 시간을 저장하기 위한 변수

    // 모니터 전용 핸들러 (TextView 갱신용)
    private final Handler monitorHandler = new Handler(Looper.getMainLooper());
    private final Runnable monitorRunnable = new Runnable() {
        @Override 
        public void run() {
            // TextView들만 갱신
            updateStatusSummaryFromFinishTimeSortedList();
            
            // 다음 모니터 갱신 예약 (monitor.interval은 초 단위이므로 1000 곱함)
            monitorHandler.postDelayed(this, ConfigManager.getInstance().getMonitorInterval() * 1000L);
            
            Timber.d("Monitor update: TextView refreshed");
        }
    };

    private final Runnable periodicUpdateRunnable = new Runnable() {
        @Override 
        public void run() {
            long currentTime = System.currentTimeMillis();
            if (lastUpdateTime != 0) {
                long actualInterval = currentTime - lastUpdateTime;
                long expectedInterval = ConfigManager.getInstance().getCarRepairInfoDisplayInterval();
                long deviation = Math.abs(actualInterval - expectedInterval);
                
                Timber.d("Expected: %dms, Actual: %dms, Deviation: %dms", 
                    expectedInterval, actualInterval, deviation);
            }
            lastUpdateTime = currentTime;
            
            Timber.i("Periodic update: Page %d", currentPageIndex);
            
            // 새로운 사이클 시작 시에만 데이터를 새로 로드하고 정렬
            if (currentPageIndex == 0) {
                // static count를 사용해서 테스트 데이터는 최초 1회만 추가
                testDataInitCount++;
                if (testDataInitCount == 1) {
                    // 테스트 데이터 추가 (실제로는 서버에서 데이터를 받아올 것)
                    addCarRepairInfoForTest();
                    Timber.i("Test data initialized for the first time (count: %d)", testDataInitCount);
                } else {
                    Timber.d("Test data already initialized (count: %d). Skipping addCarRepairInfoForTest()", testDataInitCount);
                }
                
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
            
            periodicUpdateHandler.postDelayed(this, ConfigManager.getInstance().getCarRepairInfoDisplayInterval());
        }
    };
    
    public AddOnBluehands(Context context) {
        this.context = context;
    }

    /**
     * 초기화 메서드
     */
    public void initialize(WebView repairStatusWebView, WebView videoWebView, 
                          TextView statusSummaryText, TextView carRepairStatusInfoText) {
        this.repairStatusWebView = repairStatusWebView;
        this.videoWebView = videoWebView;
        this.statusSummaryText = statusSummaryText;
        this.carRepairStatusInfoText = carRepairStatusInfoText;
        
        setupWebViews();
        startKtorServer();
    }

    private void setupWebViews() {
        if (repairStatusWebView != null) {
            repairStatusWebView.setBackgroundColor(Color.TRANSPARENT);
            repairStatusWebView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            repairStatusWebView.getSettings().setJavaScriptEnabled(true);
            repairStatusWebView.loadUrl("file:///android_asset/bluehands/status_board.html");
        }

        if (videoWebView != null) {
            // WebView 설정
            videoWebView.getSettings().setJavaScriptEnabled(true);
            videoWebView.getSettings().setMediaPlaybackRequiresUserGesture(false); // 자동 재생 허용
            videoWebView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            videoWebView.setBackgroundColor(Color.BLACK);
            
            // WebViewClient 설정 (필요시 로딩 완료 등을 처리)
            videoWebView.setWebViewClient(new WebViewClient());
            
            // 비디오 HTML 콘텐츠 생성 및 로드
            String videoHtml = createVideoHtml();
            videoWebView.loadDataWithBaseURL("file:///android_res/", videoHtml, "text/html", "UTF-8", null);
            
            Timber.i("Video WebView initialized and video loaded");
        }
    }

    private void startKtorServer() {
        try {
            ktorServer = new KtorServer(context);
            ktorServer.start();
            Timber.i("Ktor server started directly from AddOnBluehands on port 8080");
        } catch (Exception e) {
            Timber.e(e, "Failed to start Ktor server");
        }
    }

    /**
     * 주기적 업데이트 시작
     */
    public void startPeriodicUpdates() {
        // WebView 테이블 갱신: 기존 carRepairInfo interval 사용
        periodicUpdateHandler.postDelayed(periodicUpdateRunnable, ConfigManager.getInstance().getCarRepairInfoDisplayInterval());
        
        // TextView 갱신: monitor 설정에 따라 제어
        if (ConfigManager.getInstance().isMonitorEnabled()) {
            // Monitor 활성화: TextView 보이기 + 갱신 시작
            if (statusSummaryText != null) statusSummaryText.setVisibility(View.VISIBLE);
            if (carRepairStatusInfoText != null) carRepairStatusInfoText.setVisibility(View.VISIBLE);
            
            monitorHandler.removeCallbacks(monitorRunnable);
            monitorHandler.post(monitorRunnable); // 즉시 1회 실행 후 주기적 반복
            Timber.d("Monitor enabled, TextView visible and updates started");
        } else {
            // Monitor 비활성화: TextView 숨기기 + 갱신 중지
            if (statusSummaryText != null) statusSummaryText.setVisibility(View.GONE);
            if (carRepairStatusInfoText != null) carRepairStatusInfoText.setVisibility(View.GONE);
            
            monitorHandler.removeCallbacks(monitorRunnable);
            Timber.d("Monitor disabled, TextView hidden and updates stopped");
        }
    }

    /**
     * 주기적 업데이트 중지
     */
    public void stopPeriodicUpdates() {
        // WebView 갱신 중지
        periodicUpdateHandler.removeCallbacks(periodicUpdateRunnable);
        // TextView 갱신 중지
        monitorHandler.removeCallbacks(monitorRunnable);
    }

    /**
     * 리소스 정리
     */
    public void cleanup() {
        stopPeriodicUpdates();
        
        if (repairStatusWebView != null) {
            repairStatusWebView.clearCache(true);
            repairStatusWebView.clearHistory();
            repairStatusWebView.destroy();
        }
        
        if (videoWebView != null) {
            videoWebView.clearCache(true);
            videoWebView.clearHistory();
            videoWebView.destroy();
        }
        
        // Stop Ktor Server
        if (ktorServer != null) {
            try {
                ktorServer.stop();
                Timber.i("Ktor server stopped successfully");
            } catch (Exception e) {
                Timber.e(e, "Error stopping Ktor server");
            }
        }
    }

    private void updateRepairStatusWebView() {
        if (repairStatusWebView == null) {
            return;
        }

        // 데이터가 없으면 테이블을 숨기고 내용 초기화
        if (carRepairInfoDisplayList.isEmpty()) {
            String jsHide = "(function(){try{var t=document.querySelector('table');if(!t)return;" +
                    "t.style.display='none';var r=t.rows;" +
                    "if(r.length>=3){for(var i=0;i<4;i++){" +
                    "var h=r[0].cells[i]; if(h){h.textContent=''; h.className='h empty';}" +
                    "var p=r[1].cells[i]; if(p){p.textContent=''; p.className='empty';}" +
                    "var s=r[2].cells[i]; if(s){s.innerHTML=''; s.className='empty';}}}" +
                    "catch(e){console.error(e);}})();";
            repairStatusWebView.evaluateJavascript(jsHide, null);
            return;
        }

        StringBuilder jsBuilder = new StringBuilder();
        jsBuilder.append("(function(){try{var t=document.querySelector('table');if(!t)return;t.style.display='table';var r=t.rows;");
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
                
                // 차량 정보 업데이트 (두 번째 행) - 차량 번호 마스킹 적용
                String maskedPlate = maskLicensePlate(carInfo.getLicensePlateNumber());
                String plateAndModel = maskedPlate + " " + carInfo.getCarModel();
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
        } else if (carInfo.getEstimatedFinishTime() != null) {
            String timeStr = CarRepairInfo.formatSecondsToTime(CarRepairInfo.parseTimeToSeconds(carInfo.getEstimatedFinishTime()));
            String hhmmFormat = timeStr.substring(0, 5); // "HH:mm:ss"에서 "HH:mm"만 추출
            return "예상 완료 시간 : <span class=\\\"time\\\">" + hhmmFormat + "</span>";
        } else {
            return "시간 미정";
        }
    }

    public void addCarRepairInfoForTest() {
        // 테스트를 위해 carRepairInfoJobList에 더 많은 데이터 추가
        carRepairInfoJobList.clear();
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.IN_PROGRESS, "001가111", "소나타", "08:30:00", "10:30:00")); // 8:30에 요청
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.IN_PROGRESS, "002나222", "아반떼MD", "09:15:00", "12:15:00")); // 9:15에 요청
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.FINAL_INSPECTION, "003다333", "I520", "10:00:00", "13:30:00")); // 10:00에 요청
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.COMPLETED, "004라444", "모닝", "07:45:00", null)); // 7:45에 요청
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.IN_PROGRESS, "005마555", "K3", "11:20:00", "15:30:00")); // 11:20에 요청
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.IN_PROGRESS, "006바677", "투싼", "08:00:00", "09:45:00")); // 8:00에 요청
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.FINAL_INSPECTION, "007사777", "그랜저", "09:30:00", "11:20:00")); // 9:30에 요청
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.IN_PROGRESS, "008아888", "스파크", "10:45:00", "14:30:00")); // 10:45에 요청
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.IN_PROGRESS, "009자999", "레이", "12:00:00", "15:30:00")); // 12:00에 요청
        carRepairInfoJobList.add(new CarRepairInfo(CarRepairInfo.RepairStatus.COMPLETED, "0010차100", "레이스", "06:30:00", null)); // 6:30에 요청
        // 10개의 테스트 데이터로 페이지네이션 테스트 가능
    }

    /**
     * carRepairInfoJobList를 완료시간 기준으로 정렬하여 carRepairInfoFinishTimeSortedList에 저장
     */
    private void sortCarRepairInfoByFinishTime() {
        // synchronized 블록에서 안전한 스냅샷 생성
        List<CarRepairInfo> snapshot;
        synchronized(this) {
            snapshot = new ArrayList<>(carRepairInfoJobList);
        }
        
        // UI 스레드에서 정렬 및 업데이트
        ((MainActivity) context).runOnUiThread(() -> {
            carRepairInfoFinishTimeSortedList.clear();
            carRepairInfoFinishTimeSortedList.addAll(snapshot);
            
            Collections.sort(carRepairInfoFinishTimeSortedList, new Comparator<CarRepairInfo>() {
                @Override
                public int compare(CarRepairInfo info1, CarRepairInfo info2) {
                    // 완료된 작업은 맨 앞으로
                    if (info1.getRepairStatus() == CarRepairInfo.RepairStatus.COMPLETED && 
                        info2.getRepairStatus() != CarRepairInfo.RepairStatus.COMPLETED) {
                        return -1;  // info1이 완료된 경우 앞으로
                    }
                    if (info2.getRepairStatus() == CarRepairInfo.RepairStatus.COMPLETED && 
                        info1.getRepairStatus() != CarRepairInfo.RepairStatus.COMPLETED) {
                        return 1;   // info2가 완료된 경우 info2가 앞으로
                    }
                    
                    // 둘 다 완료된 경우 또는 둘 다 진행 중인 경우
                    if (info1.getRepairStatus() == CarRepairInfo.RepairStatus.COMPLETED && 
                        info2.getRepairStatus() == CarRepairInfo.RepairStatus.COMPLETED) {
                        // 완료된 작업들끼리는 차량번호 순으로 정렬
                        return info1.getLicensePlateNumber().compareTo(info2.getLicensePlateNumber());
                    }
                    
                    // 둘 다 진행 중인 경우: 완료시간 기준 정렬 (null은 맨 뒤로)
                    if (info1.getEstimatedFinishTime() == null && info2.getEstimatedFinishTime() == null) {
                        return 0;
                    }
                    if (info1.getEstimatedFinishTime() == null) {
                        return 1;
                    }
                    if (info2.getEstimatedFinishTime() == null) {
                        return -1;
                    }
                    
                    Integer thisTimeInSeconds = CarRepairInfo.parseTimeToSeconds(info1.getEstimatedFinishTime());
                    Integer otherTimeInSeconds = CarRepairInfo.parseTimeToSeconds(info2.getEstimatedFinishTime());

                    if (thisTimeInSeconds == null && otherTimeInSeconds == null) return 0;
                    if (thisTimeInSeconds == null) return 1;
                    if (otherTimeInSeconds == null) return -1;

                    return Integer.compare(thisTimeInSeconds, otherTimeInSeconds);
                }
            });
        });
        
        Timber.i("Sorted repair info list. Total items: %d", carRepairInfoFinishTimeSortedList.size());
        
        // 정렬 결과 디버그 로그
        for (int i = 0; i < carRepairInfoFinishTimeSortedList.size(); i++) {
            CarRepairInfo info = carRepairInfoFinishTimeSortedList.get(i);
            Timber.d("Sorted[%d]: %s %s - %s (RequestedTime: %s, EstimatedFinishTime: %s)", 
                i, 
                info.getLicensePlateNumber(), 
                info.getCarModel(), 
                info.getRepairStatus().name(),
                info.getRequestedTime() != null ?
                        CarRepairInfo.formatSecondsToTime(CarRepairInfo.parseTimeToSeconds(info.getRequestedTime())) : "null",
                info.getEstimatedFinishTime() != null ?
                        CarRepairInfo.formatSecondsToTime(CarRepairInfo.parseTimeToSeconds(info.getEstimatedFinishTime())) : "null"
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
    public synchronized boolean updateCarRepairInfo(String licensePlateNumber, CarRepairInfo.RepairStatus newStatus, Integer newFinishTime) {
        for (int i = 0; i < carRepairInfoJobList.size(); i++) {
            CarRepairInfo info = carRepairInfoJobList.get(i);
            if (info.getLicensePlateNumber().equals(licensePlateNumber)) {
                
                // 새로운 객체 생성 (불변성 유지)
                CarRepairInfo updatedInfo = new CarRepairInfo(
                    newStatus != null ? newStatus : info.getRepairStatus(),
                    info.getLicensePlateNumber(),
                    info.getCarModel(),
                    info.getRequestedTime(),
                    newFinishTime != null ? CarRepairInfo.formatSecondsToTime(newFinishTime) : info.getEstimatedFinishTime()
                );
                
                // 원자적 교체
                carRepairInfoJobList.set(i, updatedInfo);
                
                Timber.i("Updated repair info: %s, Status: %s, Time: %s (Thread: %s)", 
                    licensePlateNumber, newStatus, newFinishTime, Thread.currentThread().getName());
                return true;
            }
        }
        return false;
    }

    /**
     * REST API용: 모든 차량 정보 조회
     */
    public List<CarRepairInfo> getAllCarRepairInfo() {
        return new ArrayList<>(carRepairInfoJobList);
    }

    /**
     * REST API용: 특정 차량 정보 조회
     */
    public synchronized CarRepairInfo getCarRepairInfoByPlate(String licensePlateNumber) {
        for (CarRepairInfo info : carRepairInfoJobList) {
            if (info.getLicensePlateNumber().equals(licensePlateNumber)) {
                return info;
            }
        }
        return null;
    }

    /**
     * REST API용: 차량 정보 추가 (중복 체크 포함)
     */
    public synchronized boolean addCarRepairInfoApi(CarRepairInfo carRepairInfo) {
        if (carRepairInfo == null || carRepairInfo.getLicensePlateNumber() == null) {
            return false;
        }
        
        // 중복 체크와 추가를 원자적으로 처리
        for (CarRepairInfo existing : carRepairInfoJobList) {
            if (existing.getLicensePlateNumber().equals(carRepairInfo.getLicensePlateNumber())) {
                Timber.w("Car repair info already exists: %s", carRepairInfo.getLicensePlateNumber());
                return false;
            }
        }
        
        carRepairInfoJobList.add(carRepairInfo);
        Timber.i("Added new repair info via API: %s %s (Thread: %s)", 
            carRepairInfo.getLicensePlateNumber(), 
            carRepairInfo.getCarModel(),
            Thread.currentThread().getName());
        return true;
    }

    /**
     * REST API용: 차량 정보 완전 업데이트
     */
    public synchronized boolean updateCarRepairInfoApi(String licensePlateNumber, CarRepairInfo newInfo) {
        for (int i = 0; i < carRepairInfoJobList.size(); i++) {
            CarRepairInfo existing = carRepairInfoJobList.get(i);
            if (existing.getLicensePlateNumber().equals(licensePlateNumber)) {
                newInfo.setLicensePlateNumber(licensePlateNumber);
                carRepairInfoJobList.set(i, newInfo);
                
                Timber.i("Updated repair info via API: %s (Thread: %s)", 
                    licensePlateNumber, Thread.currentThread().getName());
                return true;
            }
        }
        return false;
    }

    /**
     * REST API용: 차량 정보 삭제
     */
    public synchronized boolean deleteCarRepairInfoApi(String licensePlateNumber) {
        return removeCarRepairInfo(licensePlateNumber);
    }

    private void updateStatusSummaryFromFinishTimeSortedList() {
        int doneCount = 0, inspectCount = 0, workingCount = 0;

        // carRepairInfoFinishTimeSortedList에서 상태별 개수 계산
        for (CarRepairInfo info : carRepairInfoFinishTimeSortedList) {
            switch (info.getRepairStatus()) {
                case COMPLETED:
                    doneCount++;
                    break;
                case FINAL_INSPECTION:
                    inspectCount++;
                    break;
                case IN_PROGRESS:
                    workingCount++;
                    break;
            }
        }

        // String을 미리 생성
        final String statusText = String.format("작업완료: %d대, 최종점검: %d대, 작업중: %d대",
                doneCount, inspectCount, workingCount);

        // Status Summary UI 업데이트
        ((MainActivity) context).runOnUiThread(() -> {
            if (statusSummaryText != null) {
                statusSummaryText.setText(statusText);
            }
        });

        // Car Repair Status Info List 업데이트
        updateCarRepairStatusInfoDisplay();
    }

    private void updateCarRepairStatusInfoDisplay() {
        if (carRepairStatusInfoText == null) return;

        StringBuilder infoBuilder = new StringBuilder();

        for (int i = 0; i < carRepairInfoFinishTimeSortedList.size(); i++) {
            CarRepairInfo info = carRepairInfoFinishTimeSortedList.get(i);
            String maskedPlate = info.getLicensePlateNumber();
            String line = String.format("Sorted[%d]: %s %s - %s (RequestedTime: %s, EstimatedFinishTime: %s)\n",
                    i,
                    maskedPlate,
                    info.getCarModel(),
                    info.getRepairStatus().name(),
                    info.getRequestedTime() != null ?
                            info.getRequestedTime() : "null",
                    info.getEstimatedFinishTime() != null ?
                            info.getEstimatedFinishTime() : "null"
            );
            infoBuilder.append(line);
        }

        ((MainActivity) context).runOnUiThread(() -> {
            if (carRepairStatusInfoText != null) {
                carRepairStatusInfoText.setText(infoBuilder.toString());
            }
        });
    }   
     
    // 차량 번호 마스킹 함수 (앞 2자리를 **로 표시)
    private String maskLicensePlate(String licensePlate) {
        if (licensePlate != null && licensePlate.length() >= 2) {
            return "**" + licensePlate.substring(2);
        }
        return licensePlate;
    }
     
    /**
     * 비디오 재생을 위한 HTML 콘텐츠 생성
     * sonyejin01.mp4 파일을 반복 재생하도록 설정
     */
    private String createVideoHtml() {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <meta charset='UTF-8'>" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "    <style>" +
                "        body { margin: 0; padding: 0; background-color: black; overflow: hidden; }" +
                "        video { width: 100%; height: 100%; object-fit: cover; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <video id='videoPlayer' autoplay loop muted playsinline controls='false'>" +
                "        <source src='file:///android_res/raw/sonyejin01.mp4' type='video/mp4'>" +
                "        비디오를 재생할 수 없습니다." +
                "    </video>" +
                "    <script>" +
                "        const video = document.getElementById('videoPlayer');" +
                "        video.addEventListener('loadeddata', function() {" +
                "            console.log('Video loaded successfully');" +
                "            video.play().catch(e => console.error('Play failed:', e));" +
                "        });" +
                "        video.addEventListener('error', function(e) {" +
                "            console.error('Video error:', e);" +
                "        });" +
                "        // 재생이 끝나면 자동으로 다시 재생 (loop 속성과 함께 보장)" +
                "        video.addEventListener('ended', function() {" +
                "            video.currentTime = 0;" +
                "            video.play();" +
                "        });" +
                "    </script>" +
                "</body>" +
                "</html>";
    }
}
