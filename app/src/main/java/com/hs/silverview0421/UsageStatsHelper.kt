package com.hs.silverview0421

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.util.Calendar

class UsageStatsHelper(private val context: Context) {
    
    private val usageStatsManager: UsageStatsManager? by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }
    
    /**
     * Check if the app has usage stats permission
     */
    fun hasUsageStatsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        }
        
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    /**
     * Request usage stats permission by opening settings
     */
    fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
    
    /**
     * Get total screen time for today in milliseconds
     */
    fun getTodayScreenTime(): Long {
        if (!hasUsageStatsPermission()) {
            return 0
        }
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        
        return getScreenTimeInRange(startTime, endTime)
    }
    
    /**
     * Get screen time for this app in a specific time range
     */
    fun getScreenTimeInRange(startTime: Long, endTime: Long): Long {
        if (!hasUsageStatsPermission()) {
            return 0
        }
        
        val usageStatsList = usageStatsManager?.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: return 0
        
        // Find our app's usage stats
        val packageName = context.packageName
        val usageStats = usageStatsList.find { it.packageName == packageName }
        
        return usageStats?.totalTimeInForeground ?: 0
    }
    
    /**
     * Format milliseconds to human readable time string
     */
    fun formatScreenTime(milliseconds: Long): String {
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
            minutes > 0 -> {
                val remainingSeconds = seconds % 60
                if (remainingSeconds > 0) {
                    "${minutes}m ${remainingSeconds}s"
                } else {
                    "${minutes}m"
                }
            }
            else -> "${seconds}s"
        }
    }
}
