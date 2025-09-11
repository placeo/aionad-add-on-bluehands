package com.skt.aionad.addon.server

import kotlinx.serialization.Serializable
import com.skt.aionad.addon.bluehands.CarRepairInfo
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import timber.log.Timber

@Serializable
data class CarRepairRequest(
    val licensePlateNumber: String? = null,  // optional로 변경
    val carModel: String,
    val repairStatus: String, // "COMPLETED", "FINAL_INSPECTION", "IN_PROGRESS"
    val estimatedFinishTime: String? = null // "HH:mm:ss" format
) {
    fun toCarRepairInfo(): CarRepairInfo {
        val status = when (repairStatus.uppercase()) {
            "COMPLETED" -> CarRepairInfo.RepairStatus.COMPLETED
            "FINAL_INSPECTION" -> CarRepairInfo.RepairStatus.FINAL_INSPECTION
            "IN_PROGRESS" -> CarRepairInfo.RepairStatus.IN_PROGRESS
            else -> CarRepairInfo.RepairStatus.IN_PROGRESS
        }

        // 시스템 시간을 "HH:mm:ss" 형식으로 가져와 requestedTime 설정
        val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))

        // EstimatedFinishTime 처리 로깅 및 형식 정규화
        val processedEstimatedFinishTime = if (estimatedFinishTime.isNullOrBlank()) {
            Timber.d("🔄 EstimatedFinishTime is null or blank, converting to null: '%s'", estimatedFinishTime ?: "null")
            null
        } else {
            // "HH:mm" 형식을 "HH:mm:ss" 형식으로 정규화
            val normalizedTime = if (estimatedFinishTime.split(":").size == 2) {
                "$estimatedFinishTime:00"
            } else {
                estimatedFinishTime
            }
            Timber.d("🔄 EstimatedFinishTime processed: '%s' → '%s'", estimatedFinishTime, normalizedTime)
            normalizedTime
        }

        return CarRepairInfo(
            status,
            licensePlateNumber ?: "", // null인 경우 빈 문자열 (서버에서 URL 값으로 덮어씀)
            carModel,
            currentTime, // 요청 시간을 시스템 시간으로 설정
            processedEstimatedFinishTime // 빈 문자열을 null로 변환
        )
    }
}

@Serializable
data class CarRepairResponse(
    val licensePlateNumber: String,
    val carModel: String,
    val repairStatus: String,
    val estimatedFinishTime: String? = null, // "HH:mm:ss" format
    val requestedTime: String? = null // "HH:mm:ss" format
) {
    companion object {
        fun fromCarRepairInfo(info: CarRepairInfo): CarRepairResponse {
            return CarRepairResponse(
                info.getLicensePlateNumber(),
                info.getCarModel(),
                info.getRepairStatus().name,
                info.getEstimatedFinishTime(),
                info.getRequestedTime()
            )
        }
    }
}

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null
)
