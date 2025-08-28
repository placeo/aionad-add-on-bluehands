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

    public CarRepairInfo() {
    }

    public CarRepairInfo(RepairStatus repairStatus,
                         String licensePlateNumber,
                         String carModel,
                         Integer estimatedFinishTimeMinutes) {
        this.repairStatus = repairStatus;
        this.licensePlateNumber = licensePlateNumber;
        this.carModel = carModel;
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
                && Objects.equals(estimatedFinishTimeMinutes, that.estimatedFinishTimeMinutes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repairStatus, licensePlateNumber, carModel, estimatedFinishTimeMinutes);
    }

    @Override
    public String toString() {
        return "CarRepairInfo{" +
                "repairStatus=" + repairStatus +
                ", licensePlateNumber='" + licensePlateNumber + '\'' +
                ", carModel='" + carModel + '\'' +
                ", estimatedFinishTimeMinutes=" + estimatedFinishTimeMinutes +
                '}';
    }
}