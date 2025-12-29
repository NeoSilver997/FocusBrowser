package com.hs.silverview0421

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.util.Calendar

class TimeLimitHelper(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("time_limit_prefs", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var checkRunnable: Runnable? = null
    
    companion object {
        private const val KEY_TIME_LIMIT_MINUTES = "time_limit_minutes"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
        private const val KEY_USED_TIME_TODAY = "used_time_today"
        private const val KEY_SESSION_START = "session_start"
        
        // Default time limit: 2 hours (120 minutes)
        const val DEFAULT_TIME_LIMIT_MINUTES = 120
        
        // Check interval: 1 minute
        private const val CHECK_INTERVAL_MS = 60000L
    }
    
    private var onTimeLimitReached: (() -> Unit)? = null
    private var onTimeWarning: ((Long) -> Unit)? = null
    
    /**
     * Get the configured time limit in minutes
     */
    fun getTimeLimitMinutes(): Int {
        return prefs.getInt(KEY_TIME_LIMIT_MINUTES, DEFAULT_TIME_LIMIT_MINUTES)
    }
    
    /**
     * Set the time limit in minutes
     */
    fun setTimeLimitMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_TIME_LIMIT_MINUTES, minutes).apply()
    }
    
    /**
     * Check if we need to reset the daily counter
     */
    private fun checkAndResetDaily() {
        val today = getTodayDateString()
        val lastResetDate = prefs.getString(KEY_LAST_RESET_DATE, "")
        
        if (today != lastResetDate) {
            // New day, reset the counter
            prefs.edit()
                .putString(KEY_LAST_RESET_DATE, today)
                .putLong(KEY_USED_TIME_TODAY, 0)
                .apply()
        }
    }
    
    /**
     * Get today's date as a string (YYYY-MM-DD)
     */
    private fun getTodayDateString(): String {
        val calendar = Calendar.getInstance()
        return String.format(
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
    
    /**
     * Start tracking session time
     */
    fun startSession() {
        checkAndResetDaily()
        prefs.edit().putLong(KEY_SESSION_START, System.currentTimeMillis()).apply()
        startPeriodicCheck()
    }
    
    /**
     * End tracking session time
     */
    fun endSession() {
        val sessionStart = prefs.getLong(KEY_SESSION_START, 0)
        if (sessionStart > 0) {
            val sessionDuration = System.currentTimeMillis() - sessionStart
            val currentUsedTime = prefs.getLong(KEY_USED_TIME_TODAY, 0)
            prefs.edit()
                .putLong(KEY_USED_TIME_TODAY, currentUsedTime + sessionDuration)
                .putLong(KEY_SESSION_START, 0)
                .apply()
        }
        stopPeriodicCheck()
    }
    
    /**
     * Get total used time today in milliseconds
     */
    fun getUsedTimeToday(): Long {
        checkAndResetDaily()
        var usedTime = prefs.getLong(KEY_USED_TIME_TODAY, 0)
        
        // Add current session time if active
        val sessionStart = prefs.getLong(KEY_SESSION_START, 0)
        if (sessionStart > 0) {
            usedTime += System.currentTimeMillis() - sessionStart
        }
        
        return usedTime
    }
    
    /**
     * Get remaining time in milliseconds
     */
    fun getRemainingTime(): Long {
        val timeLimitMs = getTimeLimitMinutes() * 60 * 1000L
        val usedTime = getUsedTimeToday()
        return maxOf(0, timeLimitMs - usedTime)
    }
    
    /**
     * Check if time limit has been reached
     */
    fun isTimeLimitReached(): Boolean {
        return getRemainingTime() <= 0
    }
    
    /**
     * Format milliseconds to human readable time
     */
    fun formatTime(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> {
                val remainingMinutes = minutes % 60
                if (remainingMinutes > 0) {
                    "${hours}h ${remainingMinutes}m"
                } else {
                    "${hours}h"
                }
            }
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }
    
    /**
     * Set callback for when time limit is reached
     */
    fun setOnTimeLimitReached(callback: () -> Unit) {
        onTimeLimitReached = callback
    }
    
    /**
     * Set callback for time warnings
     */
    fun setOnTimeWarning(callback: (Long) -> Unit) {
        onTimeWarning = callback
    }
    
    /**
     * Start periodic checking of time limit
     */
    private fun startPeriodicCheck() {
        checkRunnable = object : Runnable {
            override fun run() {
                val remainingTime = getRemainingTime()
                
                if (remainingTime <= 0) {
                    onTimeLimitReached?.invoke()
                    // Stop checking once limit is reached
                    stopPeriodicCheck()
                } else if (remainingTime <= 5 * 60 * 1000) { // 5 minutes warning
                    onTimeWarning?.invoke(remainingTime)
                    handler.postDelayed(this, CHECK_INTERVAL_MS)
                } else {
                    handler.postDelayed(this, CHECK_INTERVAL_MS)
                }
            }
        }
        handler.post(checkRunnable!!)
    }
    
    /**
     * Stop periodic checking
     */
    private fun stopPeriodicCheck() {
        checkRunnable?.let { handler.removeCallbacks(it) }
        checkRunnable = null
    }
    
    /**
     * Reset time limit for today (admin function)
     */
    fun resetTimeLimit() {
        prefs.edit()
            .putLong(KEY_USED_TIME_TODAY, 0)
            .putLong(KEY_SESSION_START, System.currentTimeMillis())
            .apply()
        // Restart periodic check after reset
        startPeriodicCheck()
    }
}
