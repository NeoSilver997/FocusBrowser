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
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private val defaultUrl = "https://fireflies.chiculture.org.hk"
    private val allowedDomains = listOf(
        "fireflies.chiculture.org.hk",
        "chiculture.org.hk",
        "www.ourchinastory.com"
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