package com.hs.silverview0421

import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ScreenCaptureService : Service() {
    companion object {
        private const val TAG = "ScreenCaptureService"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_INTENT = "result_intent"
        const val EXTRA_WEBVIEW_OWNER = "webview_owner"
        
        // Capture interval in milliseconds (30 seconds)
        private const val CAPTURE_INTERVAL = 10 * 1000L
        
        // Maximum retention period (7 days in milliseconds)
        const val MAX_RETENTION_PERIOD = 7 * 24 * 60 * 60 * 1000L

        private var activeWebView: WebView? = null
        
        fun setActiveWebView(webView: WebView?) {
            activeWebView = webView
        }
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
            
            // Only capture when the WebView owner is MainActivity
            if (webViewOwner != "MainActivity") {
                Log.d(TAG, "Skipping capture: WebView owner is not MainActivity")
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
                
                // Calculate hash of the image data
                val imageHash = calculateHash(screenshotData)
                
                // Check if this image is the same as the last one
                val lastHash = dbHelper.getLastCaptureHash()
                val lastId = dbHelper.getLastCaptureId()
                
                // Get current URL and title from WebView if available
                var url: String? = null
                var title: String? = null
                var domain: String? = null
                
                activeWebView?.let { webView ->
                    url = webView.url
                    title = webView.title
                    url?.let { fullUrl ->
                        try {
                            domain = android.net.Uri.parse(fullUrl).host
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing domain from URL: ${e.message}")
                        }
                    }
                }
                
                if (imageHash == lastHash && lastId != null) {
                    // This is a duplicate image, update the timestamp and browsing data of the last capture
                    // instead of creating a new entry
                    val db = dbHelper.writableDatabase
                    val values = ContentValues().apply {
                        put(ScreenCaptureDbHelper.COLUMN_LAST_VIEW_TIME, System.currentTimeMillis())
                        // Update browsing data if available
                        if (url != null) put(ScreenCaptureDbHelper.COLUMN_URL, url)
                        if (title != null) put(ScreenCaptureDbHelper.COLUMN_TITLE, title)
                        if (domain != null) put(ScreenCaptureDbHelper.COLUMN_DOMAIN, domain)
                    }
                    db.update(
                        ScreenCaptureDbHelper.TABLE_CAPTURES,
                        values,
                        "${ScreenCaptureDbHelper.COLUMN_ID} = ?",
                        arrayOf(lastId.toString())
                    )
                    Log.d(TAG, "Duplicate image detected, updated timestamp, browsing data, and last view time of existing capture")
                } else {
                    // Get current URL and title from WebView if available
                var url: String? = null
                var title: String? = null
                var domain: String? = null
                
                activeWebView?.let { webView ->
                    url = webView.url
                    title = webView.title
                    url?.let { fullUrl ->
                        try {
                            domain = android.net.Uri.parse(fullUrl).host
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing domain from URL: ${e.message}")
                        }
                    }
                }
                
                // Save to database with hash and browsing data
                dbHelper.addScreenCapture(screenshotData, imageHash, url, title, domain)
                Log.d(TAG, "Screenshot saved to database with hash: $imageHash, URL: $url, domain: $domain")
                }
                
                // Recycle bitmap
                bitmap.recycle()
            } ?: Log.w(TAG, "WebView is not available")

        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screen: ${e.message}", e)
            Log.d(TAG, "Attempting next capture in ${CAPTURE_INTERVAL/1000} seconds")
        }
    }
    
    /**
      * Calculate a SHA-256 hash of the image data
      */
     private fun calculateHash(data: ByteArray): String {
         val digest = MessageDigest.getInstance("SHA-256")
         val hashBytes = digest.digest(data)
         
         // Convert to hex string
         return hashBytes.joinToString("") { "%02x".format(it) }
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