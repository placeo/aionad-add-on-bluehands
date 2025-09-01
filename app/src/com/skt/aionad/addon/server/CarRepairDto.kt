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

        val estimatedFinishTimeMinutes = estimatedFinishTime?.let { parseTimeToMinutes(it) }
        val requestedTimeSeconds = requestedTime?.let { parseTimeToSeconds(it) }

        return CarRepairInfo(status, licensePlateNumber, carModel, requestedTimeSeconds, estimatedFinishTimeMinutes)
    }

    private fun parseTimeToMinutes(time: String): Int {
        val parts = time.split(":").map { it.toInt() }
        return parts[0] * 60 + parts[1]
    }

    private fun parseTimeToSeconds(time: String): Int {
        val parts = time.split(":").map { it.toInt() }
        return parts[0] * 3600 + parts[1] * 60 + parts[2]
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
                licensePlateNumber = info.licensePlateNumber,
                carModel = info.carModel,
                repairStatus = info.repairStatus.name,
                estimatedFinishTime = info.estimatedFinishTimeMinutes?.let { 
                    formatMinutesToTime(it) 
                },
                requestedTime = info.requestedTimeSeconds?.let { 
                    formatSecondsToTime(it) 
                }
            )
        }

        private fun formatMinutesToTime(minutes: Int): String {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            return String.format("%02d:%02d:00", hours, remainingMinutes)
        }

        private fun formatSecondsToTime(seconds: Int): String {
            val hours = seconds / 3600
            val remainingMinutes = (seconds % 3600) / 60
            val remainingSeconds = seconds % 60
            return String.format("%02d:%02d:%02d", hours, remainingMinutes, remainingSeconds)
        }
    }
}

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null
)
