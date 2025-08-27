/*
 * Copyright 2024 vschryabets@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vsh.screens

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jiangdg.usb.USBVendorId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber;

data class UsbDevice(
    val usbDevcieId: Int,
    val displayName: String,
    val vendorName: String,
    val classesStr: String
)

data class DeviceListViewState(
    val devices: List<UsbDevice> = emptyList(),
    val openPreviewDeviceId: Int? = null
)

class DeviceListViewModelFactory(
    private val usbManager: UsbManager
): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DeviceListViewModel(
            usbManager = usbManager
        ) as T
}

class DeviceListViewModel(
    private val usbManager: UsbManager
) : ViewModel() {
    private val _state = MutableStateFlow(
        DeviceListViewState()
    )
    val state: StateFlow<DeviceListViewState> = _state

    fun begin() {
        Timber.d("${this.javaClass.simpleName}, ${Throwable().stackTrace[0].methodName} called")
        loadDevices()
        Timber.d("${this.javaClass.simpleName}, ${Throwable().stackTrace[0].methodName} end")
    }

    fun stop() {
        Timber.d("${this.javaClass.simpleName}, ${Throwable().stackTrace[0].methodName} called")
    }

    fun onEnumarate() {
        Timber.d("${this.javaClass.simpleName}, ${Throwable().stackTrace[0].methodName} called")
        loadDevices()
        Timber.d("${this.javaClass.simpleName}, ${Throwable().stackTrace[0].methodName} end")
    }

    fun loadDevices() {
        Timber.d("${this.javaClass.simpleName}, ${Throwable().stackTrace[0].methodName} called")
        val usbDevices = usbManager.deviceList
        /* 
        _state.update {
            it.copy(
                devices = usbDevices.values.map { device ->
                    val vendorName = USBVendorId.vendorName(device.vendorId)
                    val vidPidStr = String.format("%04x:%04x", device.vendorId, device.productId)
                    val classesList = mutableSetOf<Int>()
                    classesList.add(device.deviceClass)
                    if (device.deviceClass == UsbConstants.USB_CLASS_MISC) {
                        for (i in 0 until device.interfaceCount) {
                            classesList.add(device.getInterface(i).interfaceClass)
                        }
                    }

                    UsbDevice(
                        usbDevcieId = device.deviceId,
                        displayName = "$vidPidStr ${device.deviceName}",
                        vendorName = if (vendorName.isEmpty()) "${device.vendorId}" else vendorName,
                        classesStr = classesList.map{
                            USBVendorId.CLASSES[it] ?: "$it"
                        }.joinToString(",\n")
                    )
                }
            )
        }
        */

        // processedUsbDevices를 _state.update 블록 외부에서 선언하고 초기화합니다.
        val processedUsbDevices = usbDevices.values.mapNotNull { device ->
            val vendorName = USBVendorId.vendorName(device.vendorId) ?: ""
            val vidPidStr = String.format("%04x:%04x", device.vendorId, device.productId)
            val classesList = mutableSetOf<Int>()
            classesList.add(device.deviceClass)
            if (device.deviceClass == UsbConstants.USB_CLASS_MISC) {
                for (i in 0 until device.interfaceCount) {
                    classesList.add(device.getInterface(i).interfaceClass)
                }
            }

            UsbDevice(
                usbDevcieId = device.deviceId,
                displayName = "$vidPidStr ${device.deviceName ?: "Unknown Device"}",
                vendorName = if (vendorName.isEmpty()) "${device.vendorId}" else vendorName,
                classesStr = classesList.mapNotNull { classCode ->
                    USBVendorId.CLASSES[classCode] ?: "$classCode"
                }.joinToString(",\\n")
            )
        }

        _state.update {
            // 이제 it.copy 내부에서는 이미 처리된 processedUsbDevices를 사용합니다.
            it.copy(devices = processedUsbDevices)
        }

        // 로그 추가: 첫 번째 USB 장치 정보 출력
        if (processedUsbDevices.isNotEmpty()) {
            val firstDevice = processedUsbDevices[0]
            // Logger.d(flag: String, msg: String) 형태에 맞게 수정
            // 문자열 합치기를 통해 하나의 msg 문자열로 만듭니다.
            val logMessage = "First USB device loaded: " +
                             "ID=${firstDevice.usbDevcieId}, " +
                             "Name='${firstDevice.displayName}', " +
                             "Vendor='${firstDevice.vendorName}', " +
                             "Classes='${firstDevice.classesStr}'"
            Timber.d("" +logMessage) // 137번째 줄 오류 발생 가능 지점
        } else {
            Timber.d("No USB devices found or processed.")
        }

        // YKK_TEST - its for removing onClick phase
        _state.update {
            it.copy(openPreviewDeviceId = processedUsbDevices[0].usbDevcieId)
        }

        Timber.d("${this.javaClass.simpleName}, ${Throwable().stackTrace[0].methodName} end")
    }

    fun onClick(device: UsbDevice) {
        Timber.d("${this.javaClass.simpleName}, ${Throwable().stackTrace[0].methodName} called")
        _state.update {
            it.copy(openPreviewDeviceId = device.usbDevcieId)
        }
        Timber.d("${this.javaClass.simpleName}, ${Throwable().stackTrace[0].methodName} end")
    }

    fun onPreviewOpened() {
        Timber.d("${this.javaClass.simpleName}, ${Throwable().stackTrace[0].methodName} called")
        _state.update {
            it.copy(openPreviewDeviceId = null)
        }
        Timber.d("${this.javaClass.simpleName}, ${Throwable().stackTrace[0].methodName} end")
    }
}