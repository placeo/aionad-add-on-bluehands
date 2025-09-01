package com.skt.aionad.addon.server

import kotlinx.serialization.Serializable
import com.skt.aionad.addon.bluehands.CarRepairInfo

@Serializable
data class CarRepairRequest(
    val licensePlateNumber: String,
    val carModel: String,
    val repairStatus: String, // "COMPLETED", "FINAL_INSPECTION", "IN_PROGRESS"
    val estimatedFinishTime: String? = null, // "HH:mm:ss" format
    val requestedTime: String? = null // "HH:mm:ss" format
) {
    fun toCarRepairInfo(): CarRepairInfo {
        val status = when (repairStatus.uppercase()) {
            "COMPLETED" -> CarRepairInfo.RepairStatus.COMPLETED
            "FINAL_INSPECTION" -> CarRepairInfo.RepairStatus.FINAL_INSPECTION
            "IN_PROGRESS" -> CarRepairInfo.RepairStatus.IN_PROGRESS
            else -> CarRepairInfo.RepairStatus.IN_PROGRESS
        }

        // Named Arguments 제거하고 순서대로 전달
        return CarRepairInfo(
            status,
            licensePlateNumber,
            carModel,
            requestedTime,
            estimatedFinishTime
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
