package com.hs.silverview0421

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private lateinit var domainSpinner: Spinner
    private val defaultUrl = "https://fireflies.chiculture.org.hk"
    
    // Map of site names to domain URLs for the dropdown
    private val siteMap = mapOf(
        "WYJJMPS" to "https://www.wyjjmps.edu.hk",
        "篇篇流螢" to "https://fireflies.chiculture.org.hk",
        "快樂閱讀花園" to "https://readinggarden.chinese.ephhk.com",
        "STAR" to "https://wapps1.hkedcity.net/cas/login?service=https%3A%2F%2Festar.edcity.hk%2F",
        "Chi Culture" to "https://chiculture.org.hk",
        "Our China Story" to "https://www.ourchinastory.com",
        "EPH Chinese" to "https://ephchinese.ephhk.com",
        "eClass WYJJMPS" to "https://eclass.wyjjmps.edu.hk"
    )
    
    // Extract just the domains for URL validation
    private val allowedDomains = listOf(
        "fireflies.chiculture.org.hk",
        "chiculture.org.hk",
        "www.ourchinastory.com",
        "www.wyjjmps.edu.hk",
        "ephchinese.ephhk.com",
        "readinggarden.chinese.ephhk.com",
        "sjrc.club",
        "eclass.wyjjmps.edu.hk",
        "star.hkedcity.net",
        "wapps1.hkedcity.net",
        "star.edcity.hk",
        "teacher.edcity.hk",
        "www.edcity.hk"
    )
    
    private lateinit var dbHelper: BrowsingHistoryDbHelper
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize database helper
        dbHelper = BrowsingHistoryDbHelper(this)
        
        // Initialize views
        webView = findViewById(R.id.webView)
        domainSpinner = findViewById(R.id.domainSpinner)
        
        // Set up the domain spinner
        setupDomainSpinner()
        
        // Set up custom action bar with clickable title
        setupActionBar()
        
        // Set up scroll detection to hide/show spinner
        setupScrollDetection()
        
        // Configure WebView settings
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
        }
        
        // Set custom WebViewClient to restrict navigation and track history
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val host = request.url.host ?: ""
                
                // Check if the domain is blocked
                if (dbHelper.isDomainBlocked(host)) {
                    // Show dialog for parent approval
                    showParentApprovalDialog(host, url)
                    return true // Block navigation for now
                }
                
                // Check if the URL's domain is in the allowed list
                val isAllowed = allowedDomains.any { domain -> host.endsWith(domain) }
                
                return if (isAllowed) {
                    // Allow navigation within allowed domains
                    false
                } else {
                    // Show dialog for parent approval for non-allowed domains
                    showParentApprovalDialog(host, url)
                    // Block navigation to non-allowed domains and stay on current page
                    true
                }
            }
            
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                
                // Add to history when page finishes loading
                val title = view.title ?: ""
                val domain = Uri.parse(url).host ?: ""
                
                // Save to database
                dbHelper.addHistoryEntry(url, title, domain)
            }
        }
        
        // Default URL will be loaded by the spinner selection
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    // Set up custom action bar with clickable title
    private fun setupActionBar() {
        supportActionBar?.let { actionBar ->
            // Set click listener for the title
            actionBar.setDisplayShowTitleEnabled(true)
            
            // Set a custom OnClickListener for the title area
            val titleId = resources.getIdentifier("action_bar_title", "id", "android")
            val titleView = findViewById<TextView>(titleId)
            
            titleView?.setOnClickListener {
                // Toggle the visibility of the domain spinner
                if (domainSpinner.visibility == View.VISIBLE) {
                    domainSpinner.visibility = View.GONE
                } else {
                    domainSpinner.visibility = View.VISIBLE
                    // Show dropdown
                    domainSpinner.performClick()
                }
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_history -> {
                // Open history activity
                startActivity(Intent(this, HistoryActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onBackPressed() {
        // Handle back button to navigate within WebView history if possible
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Close database connection
        dbHelper.close()
    }
    
    // Set up scroll detection to hide/show spinner and action bar when scrolling in WebView
    private fun setupScrollDetection() {
        // Initial Y position for scroll detection
        var lastScrollY = 0
        
        // Set up a JavaScript interface to detect scrolling
        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            // Check scroll direction
            if (scrollY > oldScrollY && scrollY > 10) {
                // Scrolling down - hide the spinner and action bar
                if (domainSpinner.visibility == View.VISIBLE) {
                    domainSpinner.visibility = View.GONE
                }
                // Hide the action bar when scrolling down
                supportActionBar?.hide()
            } else if (scrollY < oldScrollY && scrollY < 10) {
                // Scrolling up to the top - show the spinner and action bar
                if (domainSpinner.visibility == View.GONE) {
                    domainSpinner.visibility = View.VISIBLE
                }
                // Show the action bar when scrolling up to the top
                supportActionBar?.show()
            }
            
            // Update last scroll position
            lastScrollY = scrollY
        }
    }
    
    // Set up the domain spinner with site names and handle selection
    private fun setupDomainSpinner() {
        // Get site names for the spinner
        val siteNames = siteMap.keys.toList()
        
        // Create adapter for spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, siteNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        // Set the adapter to the spinner
        domainSpinner.adapter = adapter
        
        // Set selection listener
        domainSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedSite = siteNames[position]
                val url = siteMap[selectedSite] ?: defaultUrl
                
                // Load the selected URL
                webView.loadUrl(url)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Load default URL if nothing is selected
                webView.loadUrl(defaultUrl)
            }
        }
        
        // Set default selection to match defaultUrl
        val defaultSite = siteMap.entries.find { it.value == defaultUrl }?.key
        val defaultPosition = siteNames.indexOf(defaultSite)
        if (defaultPosition >= 0) {
            domainSpinner.setSelection(defaultPosition)
        } else {
            // If default URL not found in map, just load it directly
            webView.loadUrl(defaultUrl)
        }
    }
    
    // Method to show parent approval dialog
    private fun showParentApprovalDialog(domain: String, url: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_parent_approval, null)
        val passwordEditText = dialogView.findViewById<EditText>(R.id.password_edit_text)
        
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.blocked_domain_title))
            .setMessage(getString(R.string.blocked_domain_message, domain))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.approve_access)) { _, _ ->
                // Do nothing here, we'll override this below
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val password = passwordEditText.text.toString()
                if (isParentPasswordCorrect(password)) {
                    // Password correct, allow navigation
                    dialog.dismiss()
                    // Temporarily allow this domain
                    if (dbHelper.isDomainBlocked(domain)) {
                        // If it was explicitly blocked, temporarily unblock
                        dbHelper.removeBlockedDomain(domain)
                        Toast.makeText(this, getString(R.string.domain_temporarily_approved), Toast.LENGTH_SHORT).show()
                    }
                    // Load the URL
                    webView.loadUrl(url)
                } else {
                    // Password incorrect
                    passwordEditText.error = getString(R.string.incorrect_password)
                }
            }
        }
        
        dialog.show()
    }
    
    private fun isParentPasswordCorrect(password: String): Boolean {
        // For demonstration, using a hardcoded password
        // In a real app, you should use a more secure approach like encrypted shared preferences
        return password == "0919"
    }
}