package com.skt.aionad.addon.bluehands;

import java.io.Serializable;
import java.util.Objects;

public class CarRepairInfo implements Serializable, Comparable<CarRepairInfo> {

    public enum RepairStatus {
        COMPLETED,          // 작업완료
        FINAL_INSPECTION,   // 최종점검
        IN_PROGRESS         // 작업중
    }

    private RepairStatus repairStatus;
    private String licensePlateNumber;
    private String carModel;
    // 자정 이후 분(minute) 단위. 예: 15:30 -> 930
    private Integer estimatedFinishTimeMinutes;
    // 요청 시간 - 자정 이후 초(second) 단위. 예: 10:30:45 -> 37845
    private Integer requestedTimeSeconds;

    public CarRepairInfo() {
    }

    // 새로운 생성자 - requestedTimeSeconds 포함
    public CarRepairInfo(RepairStatus repairStatus,
                         String licensePlateNumber,
                         String carModel,
                         Integer requestedTimeSeconds,
                         Integer estimatedFinishTimeMinutes) {
        this.repairStatus = repairStatus;
        this.licensePlateNumber = licensePlateNumber;
        this.carModel = carModel;
        this.requestedTimeSeconds = requestedTimeSeconds;
        this.estimatedFinishTimeMinutes = estimatedFinishTimeMinutes;
    }

    public RepairStatus getRepairStatus() {
        return repairStatus;
    }

    public void setRepairStatus(RepairStatus repairStatus) {
        this.repairStatus = repairStatus;
    }

    public String getLicensePlateNumber() {
        return licensePlateNumber;
    }

    public void setLicensePlateNumber(String licensePlateNumber) {
        this.licensePlateNumber = licensePlateNumber;
    }

    public String getCarModel() {
        return carModel;
    }

    public void setCarModel(String carModel) {
        this.carModel = carModel;
    }

    public Integer getEstimatedFinishTimeMinutes() {
        return estimatedFinishTimeMinutes;
    }

    public void setEstimatedFinishTimeMinutes(Integer estimatedFinishTimeMinutes) {
        this.estimatedFinishTimeMinutes = estimatedFinishTimeMinutes;
    }

    public Integer getRequestedTimeSeconds() {
        return requestedTimeSeconds;
    }

    public void setRequestedTimeSeconds(Integer requestedTimeSeconds) {
        this.requestedTimeSeconds = requestedTimeSeconds;
    }

    // "HH:mm" -> 분(Integer). 유효하지 않으면 null 반환
    public static Integer parseTimeToMinutes(String hhmm) {
        if (hhmm == null) return null;
        String s = hhmm.trim();
        int colon = s.indexOf(':');
        if (colon <= 0 || colon == s.length() - 1) return null;
        try {
            int h = Integer.parseInt(s.substring(0, colon));
            int m = Integer.parseInt(s.substring(colon + 1));
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 분(Integer) -> "HH:mm"
    public static String formatMinutesToTime(Integer minutes) {
        if (minutes == null) return "";
        int h = Math.max(0, Math.min(23, minutes / 60));
        int m = Math.max(0, Math.min(59, minutes % 60));
        return String.format("%02d:%02d", h, m);
    }

    // "HH:mm:ss" -> 초(Integer). 유효하지 않으면 null 반환
    public static Integer parseTimeToSeconds(String hhmmss) {
        if (hhmmss == null) return null;
        String s = hhmmss.trim();
        String[] parts = s.split(":");
        if (parts.length != 3) return null;
        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            int sec = Integer.parseInt(parts[2]);
            if (h < 0 || h > 23 || m < 0 || m > 59 || sec < 0 || sec > 59) return null;
            return h * 3600 + m * 60 + sec;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 초(Integer) -> "HH:mm:ss"
    public static String formatSecondsToTime(Integer seconds) {
        if (seconds == null) return "";
        int h = Math.max(0, Math.min(23, seconds / 3600));
        int m = Math.max(0, Math.min(59, (seconds % 3600) / 60));
        int s = Math.max(0, Math.min(59, seconds % 60));
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    // 편의 메소드: "HH:mm" -> 초(Integer) (초는 0으로 처리)
    public static Integer parseTimeMinutesToSeconds(String hhmm) {
        Integer minutes = parseTimeToMinutes(hhmm);
        return minutes != null ? minutes * 60 : null;
    }

    // 편의 메소드: 초(Integer) -> "HH:mm" (초는 무시)
    public static String formatSecondsToTimeMinutes(Integer seconds) {
        if (seconds == null) return "";
        return formatMinutesToTime(seconds / 60);
    }

    @Override
    public int compareTo(CarRepairInfo o) {
        if (o == null) return -1;
        // null 은 가장 뒤로 정렬
        if (this.estimatedFinishTimeMinutes == null && o.estimatedFinishTimeMinutes == null) return 0;
        if (this.estimatedFinishTimeMinutes == null) return 1;
        if (o.estimatedFinishTimeMinutes == null) return -1;
        return Integer.compare(this.estimatedFinishTimeMinutes, o.estimatedFinishTimeMinutes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CarRepairInfo)) return false;
        CarRepairInfo that = (CarRepairInfo) o;
        return repairStatus == that.repairStatus
                && Objects.equals(licensePlateNumber, that.licensePlateNumber)
                && Objects.equals(carModel, that.carModel)
                && Objects.equals(estimatedFinishTimeMinutes, that.estimatedFinishTimeMinutes)
                && Objects.equals(requestedTimeSeconds, that.requestedTimeSeconds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repairStatus, licensePlateNumber, carModel, estimatedFinishTimeMinutes, requestedTimeSeconds);
    }

    @Override
    public String toString() {
        return "CarRepairInfo{" +
                "repairStatus=" + repairStatus +
                ", licensePlateNumber='" + licensePlateNumber + '\'' +
                ", carModel='" + carModel + '\'' +
                ", estimatedFinishTimeMinutes=" + estimatedFinishTimeMinutes +
                ", requestedTimeSeconds=" + requestedTimeSeconds +
                '}';
    }
}