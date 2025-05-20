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
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenCaptureAdapter(context: Context, cursor: Cursor) : CursorAdapter(context, cursor, 0) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    // View types
    private val VIEW_TYPE_ITEM = 0
    private val VIEW_TYPE_SECTION_HEADER = 1
    
    // Track section headers
    private val sectionHeaders = mutableMapOf<Int, String>()
    private var lastDomain: String? = null
    
    override fun getViewTypeCount(): Int {
        return 2 // Regular item and section header
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (sectionHeaders.containsKey(position)) VIEW_TYPE_SECTION_HEADER else VIEW_TYPE_ITEM
    }
    
    override fun getCount(): Int {
        return super.getCount() + sectionHeaders.size
    }
    
    override fun getItem(position: Int): Any? {
        // Adjust position for real cursor position (accounting for headers)
        if (sectionHeaders.containsKey(position)) {
            return sectionHeaders[position]
        }
        
        // Count headers before this position to get real cursor position
        val headersBeforePosition = sectionHeaders.keys.count { it <= position }
        val cursorPosition = position - headersBeforePosition
        
        return if (cursor.moveToPosition(cursorPosition)) cursor else null
    }
    
    override fun getItemId(position: Int): Long {
        if (sectionHeaders.containsKey(position)) {
            return Long.MAX_VALUE - position // Ensure unique IDs for headers
        }
        
        // Get real cursor position
        val headersBeforePosition = sectionHeaders.keys.count { it <= position }
        val cursorPosition = position - headersBeforePosition
        
        return if (cursor.moveToPosition(cursorPosition)) cursor.getLong(cursor.getColumnIndexOrThrow("_id")) else 0
    }
    
    override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
        val viewType = getItemViewType(cursor.position)
        return if (viewType == VIEW_TYPE_SECTION_HEADER) {
            LayoutInflater.from(context).inflate(R.layout.item_section_header, parent, false)
        } else {
            LayoutInflater.from(context).inflate(R.layout.item_screen_capture, parent, false)
        }
    }

    override fun bindView(view: View, context: Context, cursor: Cursor) {
        val viewType = getItemViewType(cursor.position)
        
        if (viewType == VIEW_TYPE_SECTION_HEADER) {
            // Bind section header
            val headerText = view.findViewById<TextView>(R.id.section_header_text)
            headerText.text = sectionHeaders[cursor.position] ?: "Unknown Section"
            return
        }
        
        // Get views for regular item
        val imageView = view.findViewById<ImageView>(R.id.capture_image)
        val timestampText = view.findViewById<TextView>(R.id.capture_timestamp)
        val urlText = view.findViewById<TextView>(R.id.capture_url)
        val titleText = view.findViewById<TextView>(R.id.capture_title)
        
        // Get column indices
        val timestampIndex = cursor.getColumnIndex(ScreenCaptureDbHelper.COLUMN_TIMESTAMP)
        val imageDataIndex = cursor.getColumnIndex(ScreenCaptureDbHelper.COLUMN_IMAGE_DATA)
        val imageHashIndex = cursor.getColumnIndex(ScreenCaptureDbHelper.COLUMN_IMAGE_HASH)
        val lastViewTimeIndex = cursor.getColumnIndex(ScreenCaptureDbHelper.COLUMN_LAST_VIEW_TIME)
        val urlIndex = cursor.getColumnIndex(ScreenCaptureDbHelper.COLUMN_URL)
        val titleIndex = cursor.getColumnIndex(ScreenCaptureDbHelper.COLUMN_TITLE)
        val domainIndex = cursor.getColumnIndex(ScreenCaptureDbHelper.COLUMN_DOMAIN)
        val clickCountIndex = cursor.getColumnIndex(ScreenCaptureDbHelper.COLUMN_CLICK_COUNT)
        
        // Get data from cursor
        val timestamp = cursor.getLong(timestampIndex)
        val imageData = cursor.getBlob(imageDataIndex)
        val imageHash = if (imageHashIndex >= 0) cursor.getString(imageHashIndex) else null
        val lastViewTime = if (lastViewTimeIndex >= 0 && !cursor.isNull(lastViewTimeIndex)) cursor.getLong(lastViewTimeIndex) else null
        val url = if (urlIndex >= 0 && !cursor.isNull(urlIndex)) cursor.getString(urlIndex) else null
        val title = if (titleIndex >= 0 && !cursor.isNull(titleIndex)) cursor.getString(titleIndex) else null
        val domain = if (domainIndex >= 0 && !cursor.isNull(domainIndex)) cursor.getString(domainIndex) else null
        val clickCount = if (clickCountIndex >= 0 && !cursor.isNull(clickCountIndex)) cursor.getInt(clickCountIndex) else 0
        
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
                "$dateStr (cached, last viewed: $lastViewStr) - Clicks: $clickCount"
            }
            isDuplicate -> "$dateStr (cached) - Clicks: $clickCount"
            else -> "$dateStr - Clicks: $clickCount"
        }
        
        timestampText.text = displayText
        
        // Set URL and title if available
        if (url != null) {
            urlText.visibility = View.VISIBLE
            urlText.text = url
        } else {
            urlText.visibility = View.GONE
        }
        
        if (title != null) {
            titleText.visibility = View.VISIBLE
            titleText.text = title
        } else {
            titleText.visibility = View.GONE
        }
        
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
    
    override fun changeCursor(cursor: Cursor?) {
        super.changeCursor(cursor)
        updateSectionHeaders()
    }
    
    private fun updateSectionHeaders() {
        sectionHeaders.clear()
        lastDomain = null
        
        if (cursor == null || cursor.count == 0) {
            return
        }
        
        var headerCount = 0
        val domainIndex = cursor.getColumnIndex(ScreenCaptureDbHelper.COLUMN_DOMAIN)
        
        if (domainIndex < 0) {
            return
        }
        
        cursor.moveToPosition(-1)
        while (cursor.moveToNext()) {
            val domain = if (!cursor.isNull(domainIndex)) cursor.getString(domainIndex) else null
            
            if (domain != null && domain != lastDomain) {
                // Add a section header before this item
                val position = cursor.position + headerCount
                sectionHeaders[position] = domain
                headerCount++
                lastDomain = domain
            }
        }
        
        // Reset cursor position
        cursor.moveToPosition(-1)
    }
}