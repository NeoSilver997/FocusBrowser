package com.hs.silverview0421

import android.database.Cursor
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity() {

    private lateinit var dbHelper: BrowsingHistoryDbHelper
    private lateinit var historyListView: ListView
    private lateinit var emptyView: TextView
    private lateinit var filterEditText: EditText
    private lateinit var applyFilterButton: Button
    private lateinit var clearFilterButton: Button
    private lateinit var adapter: SimpleCursorAdapter
    private var currentCursor: Cursor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.history_title)

        // Initialize database helper
        dbHelper = BrowsingHistoryDbHelper(this)

        // Initialize views
        historyListView = findViewById(R.id.history_list_view)
        emptyView = findViewById(R.id.empty_view)
        filterEditText = findViewById(R.id.filter_edit_text)
        applyFilterButton = findViewById(R.id.apply_filter_button)
        clearFilterButton = findViewById(R.id.clear_filter_button)

        // Set empty view for list
        historyListView.emptyView = emptyView

        // Set up filter buttons
        applyFilterButton.setOnClickListener {
            applyFilter()
        }

        val clearWebViewCacheButton: Button = findViewById(R.id.clear_webview_cache_button)
        val clearWebViewSessionButton: Button = findViewById(R.id.clear_webview_session_button)

        clearWebViewCacheButton.setOnClickListener {
            // Clear WebView cache
            val webView = android.webkit.WebView(this)
            webView.clearCache(true)
            webView.clearFormData()
            android.widget.Toast.makeText(this, "WebView cache cleared", android.widget.Toast.LENGTH_SHORT).show()
            // No need to reload history as this doesn't affect the history DB
        }

        clearWebViewSessionButton.setOnClickListener {
            // Clear WebView session (cookies)
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            android.widget.Toast.makeText(this, "WebView session cleared", android.widget.Toast.LENGTH_SHORT).show()
        }

        clearFilterButton.setOnClickListener {
            filterEditText.setText("")
            loadAllHistory()
        }

        // Set up list item click listener
        historyListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, _, id ->
            showHistoryItemOptions(id)
        }

        // Load history
        loadAllHistory()
    }

    private fun loadAllHistory() {
        // Close previous cursor if exists
        currentCursor?.close()

        // Get all history from database
        try {
            currentCursor = dbHelper.getAllHistory()
            setupAdapter()
        } catch (e: Exception) {
            Log.d("test",e.toString())
        }



    }

    private fun applyFilter() {
        val filterText = filterEditText.text.toString().trim()
        if (filterText.isEmpty()) {
            loadAllHistory()
            return
        }

        // Close previous cursor if exists
        currentCursor?.close()

        // Get filtered history
        currentCursor = dbHelper.getFilteredHistory(domain = filterText)
        setupAdapter()
    }

    private fun setupAdapter() {
        // Define which columns from the cursor to map to which views
        val fromColumns = arrayOf(
            BrowsingHistoryDbHelper.COLUMN_TITLE,
            BrowsingHistoryDbHelper.COLUMN_URL,
            BrowsingHistoryDbHelper.COLUMN_DOMAIN,
            BrowsingHistoryDbHelper.COLUMN_TIMESTAMP
        )

        // Define which views to map columns to
        val toViews = intArrayOf(
            R.id.history_title,
            R.id.history_url,
            R.id.history_domain,
            R.id.history_timestamp
        )

        // Create adapter
        adapter = SimpleCursorAdapter(
            this,
            R.layout.history_list_item,
            currentCursor,
            fromColumns,
            toViews,
            0
        )

        // Set adapter to convert timestamp to readable date
        adapter.setViewBinder { view, cursor, columnIndex ->
            if (columnIndex == cursor.getColumnIndex(BrowsingHistoryDbHelper.COLUMN_TIMESTAMP)) {
                val timestamp = cursor.getLong(columnIndex)
                val dateText = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", timestamp)
                (view as TextView).text = dateText.toString()
                return@setViewBinder true
            }
            false
        }

        // Set adapter to list view
        historyListView.adapter = adapter
    }

    private fun showHistoryItemOptions(id: Long) {
        val cursor = currentCursor ?: return
        if (!cursor.moveToPosition(cursor.position)) return

        val domainIndex = cursor.getColumnIndex(BrowsingHistoryDbHelper.COLUMN_DOMAIN)
        if (domainIndex < 0) return

        val domain = cursor.getString(domainIndex)
        val isBlocked = dbHelper.isDomainBlocked(domain)

        val options = arrayOf(
            if (isBlocked) getString(R.string.unblock_domain) else getString(R.string.block_domain),
            getString(R.string.clear_history)
        )

        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Toggle domain block status
                        if (isBlocked) {
                            dbHelper.removeBlockedDomain(domain)
                            Toast.makeText(this, "Unblocked: $domain", Toast.LENGTH_SHORT).show()
                        } else {
                            dbHelper.addBlockedDomain(domain)
                            Toast.makeText(this, "Blocked: $domain", Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> {
                        // Delete this history entry
                        dbHelper.deleteHistoryEntry(id)
                        // Reload history
                        applyFilter()
                    }
                }
            }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.history_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.menu_clear_all -> {
                showClearHistoryConfirmation()
                true
            }
            R.id.menu_blocked_domains -> {
                showBlockedDomains()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showClearHistoryConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_history)
            .setMessage("Are you sure you want to clear all browsing history?")
            .setPositiveButton("Yes") { _, _ ->
                dbHelper.clearHistory()
                loadAllHistory()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showBlockedDomains() {
        val cursor = dbHelper.getAllBlockedDomains()
        if (cursor.count == 0) {
            Toast.makeText(this, "No blocked domains", Toast.LENGTH_SHORT).show()
            cursor.close()
            return
        }

        val domains = mutableListOf<String>()
        while (cursor.moveToNext()) {
            val domainIndex = cursor.getColumnIndex(BrowsingHistoryDbHelper.COLUMN_DOMAIN_NAME)
            if (domainIndex >= 0) {
                domains.add(cursor.getString(domainIndex))
            }
        }
        cursor.close()

        AlertDialog.Builder(this)
            .setTitle(R.string.blocked_domains)
            .setItems(domains.toTypedArray()) { _, which ->
                val domain = domains[which]
                AlertDialog.Builder(this)
                    .setTitle("Unblock $domain?")
                    .setPositiveButton("Yes") { _, _ ->
                        dbHelper.removeBlockedDomain(domain)
                        Toast.makeText(this, "Unblocked: $domain", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        currentCursor?.close()
        dbHelper.close()
    }
}