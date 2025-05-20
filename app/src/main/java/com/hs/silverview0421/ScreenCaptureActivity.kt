package com.hs.silverview0421

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ScreenCaptureActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1
    }

    private lateinit var dbHelper: ScreenCaptureDbHelper
    private lateinit var gridView: GridView
    private lateinit var emptyView: TextView
    private lateinit var startCaptureButton: Button
    private lateinit var dateFilterSpinner: Spinner
    private lateinit var clearAllButton: Button
    private lateinit var adapter: ScreenCaptureAdapter
    
    private var isAuthenticated = false

    private lateinit var dateRanges: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_capture)

        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.screen_captures_title)

        // Initialize database helper
        dbHelper = ScreenCaptureDbHelper(this)
        
        // Initialize date ranges list
        dateRanges = listOf(
            getString(R.string.all_captures),
            getString(R.string.today),
            getString(R.string.yesterday),
            getString(R.string.last_3_days),
            getString(R.string.last_7_days)
        )

        // Initialize views
        gridView = findViewById(R.id.captures_grid_view)
        emptyView = findViewById(R.id.empty_view)
        emptyView.text = getString(R.string.no_captures)
        startCaptureButton = findViewById(R.id.start_capture_button)
        startCaptureButton.text = getString(R.string.start_capture)
        dateFilterSpinner = findViewById(R.id.date_filter_spinner)
        clearAllButton = findViewById(R.id.clear_all_button)
        clearAllButton.text = getString(R.string.clear_all)

        // Set empty view for grid
        gridView.emptyView = emptyView

        // Set up date filter spinner
        setupDateFilterSpinner()

        // Set up start capture button
        startCaptureButton.setOnClickListener {
            // Return to MainActivity to start capturing
            Toast.makeText(this, getString(R.string.use_main_activity_capture), Toast.LENGTH_LONG).show()
            finish()
        }
        
        // Set up clear all button
        clearAllButton.setOnClickListener {
            showClearAllConfirmation()
        }
        
        // Check if password protection is enabled
        checkPasswordProtection()
    }

    private fun setupDateFilterSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, dateRanges)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dateFilterSpinner.adapter = adapter

        dateFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> loadAllCaptures()
                    1 -> loadCapturesForToday()
                    2 -> loadCapturesForYesterday()
                    3 -> loadCapturesForLastNDays(3)
                    4 -> loadCapturesForLastNDays(7)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                loadAllCaptures()
            }
        }
    }

    private fun loadAllCaptures() {
        val cursor = dbHelper.getAllCaptures()
        setupAdapter(cursor)
    }

    private fun loadCapturesForToday() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.timeInMillis

        val cursor = dbHelper.getCapturesInRange(startOfDay, System.currentTimeMillis())
        setupAdapter(cursor)
    }

    private fun loadCapturesForYesterday() {
        val calendar = Calendar.getInstance()
        // Start of today
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfToday = calendar.timeInMillis

        // Start of yesterday
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val startOfYesterday = calendar.timeInMillis

        val cursor = dbHelper.getCapturesInRange(startOfYesterday, startOfToday - 1)
        setupAdapter(cursor)
    }

    private fun loadCapturesForLastNDays(days: Int) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        val startTime = calendar.timeInMillis

        val cursor = dbHelper.getCapturesInRange(startTime, System.currentTimeMillis())
        setupAdapter(cursor)
    }

    private fun setupAdapter(cursor: android.database.Cursor) {
        if (!isAuthenticated) {
            // Don't load captures if not authenticated
            return
        }
        
        adapter = ScreenCaptureAdapter(this, cursor)
        gridView.adapter = adapter

        // Set item click listener
        gridView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, id ->
            showCaptureOptions(id)
        }
    }

    private fun showCaptureOptions(id: Long) {
        val options = arrayOf(getString(R.string.view_full_size), getString(R.string.delete_capture))

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewFullSizeCapture(id)
                    1 -> deleteCapture(id)
                }
            }
            .show()
    }

    private fun viewFullSizeCapture(id: Long) {
        // Update the last view time for this capture
        dbHelper.updateLastViewTime(id)
        
        // Use a background thread to load the image to prevent UI hangs
        Thread {
            try {
                // Get the capture from database
                val cursor = dbHelper.getCaptureById(id)
                if (cursor.moveToFirst()) {
                    val imageDataIndex = cursor.getColumnIndex(ScreenCaptureDbHelper.COLUMN_IMAGE_DATA)
                    val imageData = cursor.getBlob(imageDataIndex)
                    
                    // Update UI on main thread
                    runOnUiThread {
                        // Show a dialog with the full-size image
                        val dialogView = layoutInflater.inflate(R.layout.dialog_full_image, null)
                        val imageView = dialogView.findViewById<ImageView>(R.id.full_image_view)
                        
                        // Load the bitmap in background thread
                        Thread {
                            val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                            runOnUiThread {
                                imageView.setImageBitmap(bitmap)
                                
                                // Set up pinch-to-zoom and pan functionality
                                setupImageViewZoom(imageView, bitmap)
                            }
                        }.start()
                        
                        // Create a fullscreen dialog
                        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                            .setView(dialogView)
                            .create()
                            
                        // Add a click listener to dismiss on tap
                        dialogView.setOnClickListener {
                            dialog.dismiss()
                        }
                        
                        dialog.show()
                    }
                }
                cursor.close()
            } catch (e: Exception) {
                Log.e("ScreenCaptureActivity", "Error viewing capture: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun deleteCapture(id: Long) {
        val deleted = dbHelper.deleteCapture(id)
        if (deleted > 0) {
            Toast.makeText(this, getString(R.string.capture_deleted), Toast.LENGTH_SHORT).show()
            // Refresh the current view
            val position = dateFilterSpinner.selectedItemPosition
            dateFilterSpinner.setSelection(position)
        }
    }

    private fun requestScreenCapturePermission() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            REQUEST_MEDIA_PROJECTION
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == Activity.RESULT_OK && data != null) {
            startScreenCaptureService(resultCode, data)
        }
    }

    private fun startScreenCaptureService(resultCode: Int, data: Intent) {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_INTENT, data)
        }
        startService(intent)
        Toast.makeText(this, getString(R.string.capture_service_started), Toast.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupImageViewZoom(imageView: ImageView, bitmap: android.graphics.Bitmap) {
        // This is a simplified implementation - for a production app, consider using a library
        // like PhotoView for better zoom and pan functionality
        var scale = 1f
        var scaleFactor = 1.0f
        val matrix = Matrix()
        imageView.scaleType = ImageView.ScaleType.MATRIX
        
        imageView.setOnTouchListener { v, event ->
            when (event.action and android.view.MotionEvent.ACTION_MASK) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    // Simple implementation - in a real app, implement proper pinch-to-zoom
                    matrix.reset()
                    matrix.postScale(scale, scale, imageView.width / 2f, imageView.height / 2f)
                    imageView.imageMatrix = matrix
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    // Toggle between fit center and zoomed view on tap
                    scale = if (scale > 1f) 1f else 2f
                    matrix.reset()
                    matrix.postScale(scale, scale, imageView.width / 2f, imageView.height / 2f)
                    imageView.imageMatrix = matrix
                    true
                }
                else -> false
            }
        }
    }
    
    private fun checkPasswordProtection() {
        if (dbHelper.isPasswordSet()) {
            // Password is set, show authentication dialog
            showPasswordDialog(false)
        } else {
            // No password set, ask if user wants to set one
            showSetPasswordPrompt()
            isAuthenticated = true
            loadAllCaptures()
        }
    }
    
    private fun showSetPasswordPrompt() {
        AlertDialog.Builder(this)
            .setTitle(R.string.password_protection)
            .setMessage(R.string.set_password_prompt)
            .setPositiveButton(R.string.yes) { _, _ ->
                showPasswordDialog(true)
            }
            .setNegativeButton(R.string.no) { _, _ ->
                // User doesn't want to set a password
                Toast.makeText(this, R.string.no_password_set, Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun showPasswordDialog(isSettingPassword: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_password, null)
        val passwordEditText = dialogView.findViewById<EditText>(R.id.password_edit_text)
        val confirmPasswordEditText = dialogView.findViewById<EditText>(R.id.confirm_password_edit_text)
        val passwordMessage = dialogView.findViewById<TextView>(R.id.password_message)
        val titleTextView = dialogView.findViewById<TextView>(R.id.password_dialog_title)
        
        if (isSettingPassword) {
            titleTextView.text = getString(R.string.set_password)
            confirmPasswordEditText.visibility = View.VISIBLE
        } else {
            titleTextView.text = getString(R.string.enter_password)
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(R.string.ok, null) // Set to null initially
            .setNegativeButton(if (isSettingPassword) R.string.cancel else R.string.exit) { _, _ ->
                if (!isSettingPassword) {
                    // User canceled authentication, exit activity
                    finish()
                }
            }
            .create()
        
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val password = passwordEditText.text.toString()
                
                if (password.isEmpty()) {
                    passwordMessage.text = getString(R.string.password_empty)
                    passwordMessage.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                
                if (isSettingPassword) {
                    // Setting a new password
                    val confirmPassword = confirmPasswordEditText.text.toString()
                    if (password != confirmPassword) {
                        passwordMessage.text = getString(R.string.passwords_dont_match)
                        passwordMessage.visibility = View.VISIBLE
                        return@setOnClickListener
                    }
                    
                    // Save the password
                    dbHelper.setPassword(password)
                    Toast.makeText(this, R.string.password_set, Toast.LENGTH_SHORT).show()
                    isAuthenticated = true
                    loadAllCaptures()
                    dialog.dismiss()
                } else {
                    // Verifying password
                    if (dbHelper.verifyPassword(password)) {
                        isAuthenticated = true
                        loadAllCaptures()
                        dialog.dismiss()
                    } else {
                        passwordMessage.text = getString(R.string.incorrect_password)
                        passwordMessage.visibility = View.VISIBLE
                    }
                }
            }
        }
        
        dialog.show()
    }
    
    private fun showClearAllConfirmation() {
        if (dbHelper.getCaptureCount() == 0) {
            Toast.makeText(this, R.string.no_captures_to_clear, Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_all_captures)
            .setMessage(R.string.clear_all_confirmation)
            .setPositiveButton(R.string.yes) { _, _ ->
                val deletedCount = dbHelper.clearAllCaptures()
                Toast.makeText(this, getString(R.string.cleared_captures, deletedCount), Toast.LENGTH_SHORT).show()
                loadAllCaptures()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Close database connection
        dbHelper.close()
    }
}