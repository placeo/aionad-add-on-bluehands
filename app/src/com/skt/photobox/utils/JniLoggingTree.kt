package com.skt.photobox.utils

import android.util.Log
import timber.log.Timber

/**
 * A Timber Tree that bridges to a JNI function.
 * This allows Timber logs from Java/Kotlin to be passed to the native C++ logging backend.
 */
class JniLoggingTree : Timber.Tree() {

    init {
        try {
            // Ensure the native library containing the JNI function is loaded.
            System.loadLibrary("PhotoBox")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("JniLoggingTree", "Failed to load native library 'PhotoBox'. JNI logging will not work.", e)
        }
    }

    /**
     * This native function is implemented in PhotoBoxMain.cpp
     * and calls the C++ FRONTEND_LOG* macros.
     */
    private external fun nativeLog(priority: Int, tag: String, message: String)

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // The console logger (ColorLogTree) will handle printing the stack trace for the throwable.
        // We just pass the core log data to the JNI layer.
        nativeLog(priority, tag ?: "Frontend", message)
    }
} 