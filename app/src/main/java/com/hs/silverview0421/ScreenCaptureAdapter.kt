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
        
        // Get data from cursor
        val timestamp = cursor.getLong(timestampIndex)
        val imageData = cursor.getBlob(imageDataIndex)
        
        // Convert timestamp to readable date
        val date = Date(timestamp)
        timestampText.text = dateFormat.format(date)
        
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