package com.hs.silverview0421

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Canvas
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class ScreenCaptureService : Service() {
    companion object {
        private const val TAG = "ScreenCaptureService"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_INTENT = "result_intent"
        const val EXTRA_WEBVIEW_OWNER = "webview_owner"
        
        // Capture interval in milliseconds (30 seconds)
        private const val CAPTURE_INTERVAL = 30 * 1000L
        
        // Maximum retention period (7 days in milliseconds)
        const val MAX_RETENTION_PERIOD = 7 * 24 * 60 * 60 * 1000L
    }
    
    private lateinit var dbHelper: ScreenCaptureDbHelper
    private var webViewOwner: String? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val captureRunnable = object : Runnable {
        override fun run() {
            captureScreen()
            handler.postDelayed(this, CAPTURE_INTERVAL)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        dbHelper = ScreenCaptureDbHelper(this)
        
        // Clean up old captures
        cleanupOldCaptures()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            webViewOwner = intent.getStringExtra(EXTRA_WEBVIEW_OWNER)
            if (webViewOwner != null) {
                startCapture()
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }
    
    private fun startCapture() {
        // Start periodic capture immediately
        captureRunnable.run()
    }
    }
    
    companion object {
        private var activeWebView: WebView? = null
        
        fun setActiveWebView(webView: WebView?) {
            activeWebView = webView
        }
    }
    
    private fun stopCapture() {
        handler.removeCallbacks(captureRunnable)
        webViewOwner = null
    }
    
    private fun captureScreen() {
        try {
            if (webViewOwner == null) {
                Log.w(TAG, "No active WebView owner")
                return
            }
            
            activeWebView?.let { view ->
                // Create a bitmap of the WebView's dimensions
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                
                // Draw the WebView content onto the bitmap
                view.draw(canvas)
                
                // Compress bitmap to JPEG
                val outputStream = ByteArrayOutputStream()
                val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
                Log.d(TAG, "Bitmap compressed to JPEG: $compressed, size=${outputStream.size()}")
                
                val screenshotData = outputStream.toByteArray()
                // Save to database
                dbHelper.addScreenCapture(screenshotData)
                Log.d(TAG, "Screenshot saved to database.")
                
                // Recycle bitmap
                bitmap.recycle()
            } ?: Log.w(TAG, "WebView is not available")

        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screen: ${e.message}", e)
            Log.d(TAG, "Attempting next capture in ${CAPTURE_INTERVAL/1000} seconds")
        }
    }
    
    private fun cleanupOldCaptures() {
        val cutoffTime = System.currentTimeMillis() - MAX_RETENTION_PERIOD
        dbHelper.deleteOldCaptures(cutoffTime)
        
        // Schedule periodic cleanup
        Handler(Looper.getMainLooper()).postDelayed({
            cleanupOldCaptures()
        }, TimeUnit.HOURS.toMillis(12)) // Run cleanup every 12 hours
    }
}