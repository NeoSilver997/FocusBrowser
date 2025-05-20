package com.hs.silverview0421

import android.content.Context
import android.database.Cursor
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CursorAdapter
import android.widget.ImageView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenCaptureAdapter(context: Context, cursor: Cursor) : CursorAdapter(context, cursor, 0) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.item_screen_capture, parent, false)
    }

    override fun bindView(view: View, context: Context, cursor: Cursor) {
        // Get views
        val imageView = view.findViewById<ImageView>(R.id.capture_image)
        val timestampText = view.findViewById<TextView>(R.id.capture_timestamp)
        
        // Get column indices
        val timestampIndex = cursor.getColumnIndex(ScreenCaptureDbHelper.COLUMN_TIMESTAMP)
        val imageDataIndex = cursor.getColumnIndex(ScreenCaptureDbHelper.COLUMN_IMAGE_DATA)
        val imageHashIndex = cursor.getColumnIndex(ScreenCaptureDbHelper.COLUMN_IMAGE_HASH)
        val lastViewTimeIndex = cursor.getColumnIndex(ScreenCaptureDbHelper.COLUMN_LAST_VIEW_TIME)
        
        // Get data from cursor
        val timestamp = cursor.getLong(timestampIndex)
        val imageData = cursor.getBlob(imageDataIndex)
        val imageHash = if (imageHashIndex >= 0) cursor.getString(imageHashIndex) else null
        val lastViewTime = if (lastViewTimeIndex >= 0 && !cursor.isNull(lastViewTimeIndex)) cursor.getLong(lastViewTimeIndex) else null
        
        // Convert timestamp to readable date
        val date = Date(timestamp)
        val dateStr = dateFormat.format(date)
        
        // If this is a cached image (reused from previous capture), indicate it
        val position = cursor.position
        var isDuplicate = false
        
        if (position < cursor.count - 1 && imageHash != null) {
            // Check if the next image has the same hash
            val currentPosition = cursor.position
            if (cursor.moveToPosition(position + 1)) {
                val nextHashIndex = cursor.getColumnIndex(ScreenCaptureDbHelper.COLUMN_IMAGE_HASH)
                if (nextHashIndex >= 0) {
                    val nextHash = cursor.getString(nextHashIndex)
                    isDuplicate = imageHash == nextHash
                }
            }
            // Restore cursor position
            cursor.moveToPosition(currentPosition)
        }
        
        // Format the display text based on whether it's a duplicate and has last view time
        val displayText = when {
            isDuplicate && lastViewTime != null -> {
                val lastViewDate = Date(lastViewTime)
                val lastViewStr = dateFormat.format(lastViewDate)
                "$dateStr (cached, last viewed: $lastViewStr)"
            }
            isDuplicate -> "$dateStr (cached)"
            else -> dateStr
        }
        
        timestampText.text = displayText
        
        // Use a background thread to decode the bitmap and set it to the ImageView
        Thread {
            try {
                // Set bitmap decoding options to reduce memory usage
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2  // Reduce image size to 1/4 of original (half width, half height)
                }
                val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)
                
                // Update UI on main thread
                Handler(Looper.getMainLooper()).post {
                    imageView.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                Log.e("ScreenCaptureAdapter", "Error loading image: ${e.message}")
            }
        }.start()
    }
}