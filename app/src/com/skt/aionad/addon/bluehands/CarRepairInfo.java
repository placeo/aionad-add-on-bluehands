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
    private String estimatedFinishTime; // "HH:mm:ss" 형식
    private String requestedTime;       // "HH:mm:ss" 형식

    public CarRepairInfo() {
    }

    public CarRepairInfo(RepairStatus repairStatus,
                         String licensePlateNumber,
                         String carModel,
                         String requestedTime,
                         String estimatedFinishTime) {
        this.repairStatus = repairStatus;
        this.licensePlateNumber = licensePlateNumber;
        this.carModel = carModel;
        this.requestedTime = requestedTime;
        this.estimatedFinishTime = estimatedFinishTime;
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

    public String getEstimatedFinishTime() {
        return estimatedFinishTime;
    }

    public void setEstimatedFinishTime(String estimatedFinishTime) {
        this.estimatedFinishTime = estimatedFinishTime;
    }

    public String getRequestedTime() {
        return requestedTime;
    }

    public void setRequestedTime(String requestedTime) {
        this.requestedTime = requestedTime;
    }

    // "HH:mm:ss" 또는 "HH:mm" -> 초(Integer). 유효하지 않으면 null 반환
    public static Integer parseTimeToSeconds(String hhmmss) {
        if (hhmmss == null) return null;
        String s = hhmmss.trim();
        if (s.isEmpty()) return null; // 빈 문자열 처리 추가
        String[] parts = s.split(":");
        
        // "HH:mm:ss" 형식과 "HH:mm" 형식 모두 지원
        if (parts.length == 2) {
            // "HH:mm" 형식인 경우 초를 0으로 추가
            try {
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                if (h < 0 || h > 23 || m < 0 || m > 59) return null;
                return h * 3600 + m * 60; // 초는 0
            } catch (NumberFormatException e) {
                return null;
            }
        } else if (parts.length == 3) {
            // "HH:mm:ss" 형식
            try {
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                int sec = Integer.parseInt(parts[2]);
                if (h < 0 || h > 23 || m < 0 || m > 59 || sec < 0 || sec > 59) return null;
                return h * 3600 + m * 60 + sec;
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null; // 잘못된 형식
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

    @Override
    public int compareTo(CarRepairInfo o) {
        if (o == null) return -1;
        if (this.estimatedFinishTime == null && o.estimatedFinishTime == null) return 0;
        if (this.estimatedFinishTime == null) return 1;
        if (o.estimatedFinishTime == null) return -1;

        Integer thisTimeInSeconds = parseTimeToSeconds(this.estimatedFinishTime);
        Integer otherTimeInSeconds = parseTimeToSeconds(o.estimatedFinishTime);

        if (thisTimeInSeconds == null && otherTimeInSeconds == null) return 0;
        if (thisTimeInSeconds == null) return 1;
        if (otherTimeInSeconds == null) return -1;

        return Integer.compare(thisTimeInSeconds, otherTimeInSeconds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CarRepairInfo)) return false;
        CarRepairInfo that = (CarRepairInfo) o;
        return repairStatus == that.repairStatus
                && Objects.equals(licensePlateNumber, that.licensePlateNumber)
                && Objects.equals(carModel, that.carModel)
                && Objects.equals(estimatedFinishTime, that.estimatedFinishTime)
                && Objects.equals(requestedTime, that.requestedTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repairStatus, licensePlateNumber, carModel, estimatedFinishTime, requestedTime);
    }

    @Override
    public String toString() {
        return "CarRepairInfo{" +
                "repairStatus=" + repairStatus +
                ", licensePlateNumber='" + licensePlateNumber + '\'' +
                ", carModel='" + carModel + '\'' +
                ", estimatedFinishTime='" + estimatedFinishTime + '\'' +
                ", requestedTime='" + requestedTime + '\'' +
                '}';
    }
}