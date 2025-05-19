package com.hs.silverview0421

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class ScreenCaptureService : Service() {
    companion object {
        private const val TAG = "ScreenCaptureService"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_INTENT = "result_intent"
        
        // Capture interval in milliseconds (30 seconds)
        private const val CAPTURE_INTERVAL = 30 * 1000L
        
        // Maximum retention period (7 days in milliseconds)
        const val MAX_RETENTION_PERIOD = 7 * 24 * 60 * 60 * 1000L
    }
    
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var windowManager: WindowManager
    private lateinit var dbHelper: ScreenCaptureDbHelper
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var displayWidth = 0
    private var displayHeight = 0
    private var displayDensity = 0
    
    private val handler = Handler(Looper.getMainLooper())
    private val captureRunnable = object : Runnable {
        override fun run() {
            captureScreen()
            handler.postDelayed(this, CAPTURE_INTERVAL)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        dbHelper = ScreenCaptureDbHelper(this)
        
        // Get display metrics
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        displayWidth = metrics.widthPixels / 2  // Reduce size to save storage
        displayHeight = metrics.heightPixels / 2 // Reduce size to save storage
        displayDensity = metrics.densityDpi
        
        // Clean up old captures
        cleanupOldCaptures()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultIntent = intent.getParcelableExtra<Intent>(EXTRA_RESULT_INTENT)
            
            if (resultCode != 0 && resultIntent != null) {
                startCapture(resultCode, resultIntent)
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
    
    private fun startCapture(resultCode: Int, resultIntent: Intent) {
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultIntent)
        mediaProjection?.let { projection ->
            // Create ImageReader
            imageReader = ImageReader.newInstance(
                displayWidth, displayHeight, PixelFormat.RGBA_8888, 2
            )
            
            // Create virtual display
            virtualDisplay = projection.createVirtualDisplay(
                "ScreenCapture",
                displayWidth, displayHeight, displayDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
            
            // Start periodic capture
            handler.post(captureRunnable)
        }
    }
    
    private fun stopCapture() {
        handler.removeCallbacks(captureRunnable)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }
    
    private fun captureScreen() {
        try {
            imageReader?.acquireLatestImage()?.use { image ->
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * displayWidth
                
                // Create bitmap
                val bitmap = Bitmap.createBitmap(
                    displayWidth + rowPadding / pixelStride,
                    displayHeight, Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                
                // Compress bitmap to JPEG
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
                val screenshotData = outputStream.toByteArray()
                
                // Save to database
                dbHelper.addScreenCapture(screenshotData)
                
                // Recycle bitmap
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screen: ${e.message}")
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