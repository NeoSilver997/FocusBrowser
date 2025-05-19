package com.hs.silverview0421

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var adapter: ScreenCaptureAdapter

    private val dateRanges = listOf(
        getString(R.string.all_captures),
        getString(R.string.today),
        getString(R.string.yesterday),
        getString(R.string.last_3_days),
        getString(R.string.last_7_days)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_capture)

        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.screen_captures_title)

        // Initialize database helper
        dbHelper = ScreenCaptureDbHelper(this)

        // Initialize views
        gridView = findViewById(R.id.captures_grid_view)
        emptyView = findViewById(R.id.empty_view)
        emptyView.text = getString(R.string.no_captures)
        startCaptureButton = findViewById(R.id.start_capture_button)
        startCaptureButton.text = getString(R.string.start_capture)
        dateFilterSpinner = findViewById(R.id.date_filter_spinner)

        // Set empty view for grid
        gridView.emptyView = emptyView

        // Set up date filter spinner
        setupDateFilterSpinner()

        // Set up start capture button
        startCaptureButton.setOnClickListener {
            requestScreenCapturePermission()
        }

        // Load captures
        loadAllCaptures()
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
        // In a real app, you would open a full-screen activity to display the image
        Toast.makeText(this, "Viewing capture #$id", Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        super.onDestroy()
        // Close database connection
        dbHelper.close()
    }
}