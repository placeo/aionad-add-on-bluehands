package com.skt.photobox.server

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

class KtorServerService : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private val ktorServer = KtorServer()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                ktorServer.start()
                Timber.i("Ktor server started on port 8080")
            } catch (e: Exception) {
                Timber.e(e, "Error starting Ktor server")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        ktorServer.stop()
        job.cancel()
        Timber.i("Ktor server stopped")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
} 