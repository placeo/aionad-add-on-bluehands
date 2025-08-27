package com.jiangdg.usb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import timber.log.Timber;

public class GlobalUsbPermissionReceiver extends BroadcastReceiver {

    // private static final String TAG = "GlobalUsbPermReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Timber.d("GlobalUsbPermissionReceiver onReceive, action: " + action);

        if (USBMonitor.ACTION_USB_PERMISSION.equals(action)) {
            synchronized (this) { // 동기화는 USBMonitor의 방식과 유사하게 유지
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);

                if (device != null) {
                    Timber.i("GlobalReceiver: Received permission result for device: " + device.getDeviceName() + ", granted: " + granted);
                    // TODO: 여기서 실제 USBMonitor 인스턴스에게 결과를 전달해야 함
                    // 예: EventBus, LocalBroadcastManager, 또는 static 콜백 인터페이스 등
                    // 우선은 로그로만 확인
                    if (granted) {
                        Timber.i("GlobalReceiver: Permission GRANTED for " + device.getDeviceName());
                    } else {
                        Timber.w("GlobalReceiver: Permission DENIED for " + device.getDeviceName());
                    }
                } else {
                    Timber.w("GlobalReceiver: device is NULL in received intent!");
                }
            }
        }
    }
} 