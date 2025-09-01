package com.skt.aionad.addon.server

import kotlinx.serialization.Serializable
import com.skt.aionad.addon.bluehands.CarRepairInfo

@Serializable
data class CarRepairRequest(
    val licensePlateNumber: String,
    val carModel: String,
    val repairStatus: String, // "COMPLETED", "FINAL_INSPECTION", "IN_PROGRESS"
    val estimatedFinishTimeMinutes: Int? = null,
    val requestedTimeSeconds: Int? = null
) {
    fun toCarRepairInfo(): CarRepairInfo {
        val status = when (repairStatus.uppercase()) {
            "COMPLETED" -> CarRepairInfo.RepairStatus.COMPLETED
            "FINAL_INSPECTION" -> CarRepairInfo.RepairStatus.FINAL_INSPECTION
            "IN_PROGRESS" -> CarRepairInfo.RepairStatus.IN_PROGRESS
            else -> CarRepairInfo.RepairStatus.IN_PROGRESS
        }
        return CarRepairInfo(status, licensePlateNumber, carModel, requestedTimeSeconds, estimatedFinishTimeMinutes)
    }
}

@Serializable
data class CarRepairResponse(
    val licensePlateNumber: String,
    val carModel: String,
    val repairStatus: String,
    val estimatedFinishTimeMinutes: Int? = null,
    val requestedTimeSeconds: Int? = null,
    val estimatedFinishTime: String? = null, // "HH:mm" format
    val requestedTime: String? = null // "HH:mm:ss" format
) {
    companion object {
        fun fromCarRepairInfo(info: CarRepairInfo): CarRepairResponse {
            return CarRepairResponse(
                licensePlateNumber = info.licensePlateNumber,
                carModel = info.carModel,
                repairStatus = info.repairStatus.name,
                estimatedFinishTimeMinutes = info.estimatedFinishTimeMinutes,
                requestedTimeSeconds = info.requestedTimeSeconds,
                estimatedFinishTime = info.estimatedFinishTimeMinutes?.let { 
                    CarRepairInfo.formatMinutesToTime(it) 
                },
                requestedTime = info.requestedTimeSeconds?.let { 
                    CarRepairInfo.formatSecondsToTime(it) 
                }
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
