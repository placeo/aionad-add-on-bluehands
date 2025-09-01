package com.skt.aionad.addon.server

import android.app.Service
import android.content.Intent
import android.os.IBinder
import timber.log.Timber

class KtorServerService : Service() {

    override fun onCreate() {
        super.onCreate()
        Timber.i("KtorServerService created (deprecated - server now managed by MainActivity)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.w("KtorServerService.onStartCommand called but server is now managed by MainActivity")
        // Do nothing - server is now managed directly by MainActivity
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("KtorServerService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 