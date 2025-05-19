package com.hs.silverview0421

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private val defaultUrl = "https://fireflies.chiculture.org.hk"
    private val allowedDomains = listOf(
        "fireflies.chiculture.org.hk",
        "chiculture.org.hk"
    )
    
    private lateinit var dbHelper: BrowsingHistoryDbHelper
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize database helper
        dbHelper = BrowsingHistoryDbHelper(this)
        
        webView = findViewById(R.id.webView)
        
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
                    return true // Block navigation
                }
                
                // Check if the URL's domain is in the allowed list
                val isAllowed = allowedDomains.any { domain -> host.endsWith(domain) }
                
                return if (isAllowed) {
                    // Allow navigation within allowed domains
                    false
                } else {
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
        
        // Load the default URL
        webView.loadUrl(defaultUrl)
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
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
}