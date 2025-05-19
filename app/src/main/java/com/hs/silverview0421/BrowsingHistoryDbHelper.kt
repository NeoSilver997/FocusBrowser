package com.hs.silverview0421

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Date

class BrowsingHistoryDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "browsing_history.db"
        private const val DATABASE_VERSION = 1

        // History table
        const val TABLE_HISTORY = "history"
        const val COLUMN_ID = "id"
        const val COLUMN_URL = "url"
        const val COLUMN_TITLE = "title"
        const val COLUMN_DOMAIN = "domain"
        const val COLUMN_TIMESTAMP = "timestamp"

        // Blocked domains table
        const val TABLE_BLOCKED_DOMAINS = "blocked_domains"
        const val COLUMN_DOMAIN_ID = "id"
        const val COLUMN_DOMAIN_NAME = "domain_name"
        const val COLUMN_BLOCK_DATE = "block_date"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create history table
        val createHistoryTable = "CREATE TABLE $TABLE_HISTORY ("
                .plus("$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, ")
                .plus("$COLUMN_URL TEXT NOT NULL, ")
                .plus("$COLUMN_TITLE TEXT, ")
                .plus("$COLUMN_DOMAIN TEXT NOT NULL, ")
                .plus("$COLUMN_TIMESTAMP INTEGER NOT NULL)")

        // Create blocked domains table
        val createBlockedDomainsTable = "CREATE TABLE $TABLE_BLOCKED_DOMAINS ("
                .plus("$COLUMN_DOMAIN_ID INTEGER PRIMARY KEY AUTOINCREMENT, ")
                .plus("$COLUMN_DOMAIN_NAME TEXT NOT NULL UNIQUE, ")
                .plus("$COLUMN_BLOCK_DATE INTEGER NOT NULL)")

        db.execSQL(createHistoryTable)
        db.execSQL(createBlockedDomainsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle database upgrades if needed
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BLOCKED_DOMAINS")
        onCreate(db)
    }

    // Add a URL to history
    fun addHistoryEntry(url: String, title: String?, domain: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_URL, url)
            put(COLUMN_TITLE, title)
            put(COLUMN_DOMAIN, domain)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
        }
        db.insert(TABLE_HISTORY, null, values)
        db.close()
    }

    // Get all history entries
    fun getAllHistory(): Cursor {
        val db = readableDatabase
        return db.rawQuery("SELECT id AS _id, url, title, domain, timestamp FROM $TABLE_HISTORY ORDER BY timestamp DESC", null)
    }

    fun getFilteredHistory(domain: String? = null, url: String? = null): Cursor {
        val db = readableDatabase
        val selection = StringBuilder()
        val selectionArgs = mutableListOf<String>()
        
        if (!domain.isNullOrEmpty()) {
            selection.append("$COLUMN_DOMAIN LIKE ?")
            selectionArgs.add("%$domain%")
        }
        
        if (!url.isNullOrEmpty()) {
            if (selection.isNotEmpty()) selection.append(" AND ")
            selection.append("$COLUMN_URL LIKE ?")
            selectionArgs.add("%$url%")
        }
        
        val query = "SELECT id AS _id, url, title, domain, timestamp FROM $TABLE_HISTORY" +
                if (selection.isNotEmpty()) " WHERE ${selection.toString()}" else "" +
                " ORDER BY timestamp DESC"
        
        return db.rawQuery(query, selectionArgs.toTypedArray())
    }

    // Add a domain to block list
    fun addBlockedDomain(domain: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DOMAIN_NAME, domain)
            put(COLUMN_BLOCK_DATE, System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_BLOCKED_DOMAINS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    // Remove a domain from block list
    fun removeBlockedDomain(domain: String) {
        val db = writableDatabase
        db.delete(TABLE_BLOCKED_DOMAINS, "$COLUMN_DOMAIN_NAME = ?", arrayOf(domain))
        db.close()
    }

    // Check if a domain is blocked
    fun isDomainBlocked(domain: String): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_BLOCKED_DOMAINS,
            arrayOf(COLUMN_DOMAIN_NAME),
            "$COLUMN_DOMAIN_NAME = ?",
            arrayOf(domain),
            null,
            null,
            null
        )
        val isBlocked = cursor.count > 0
        cursor.close()
        return isBlocked
    }

    // Get all blocked domains
    fun getAllBlockedDomains(): Cursor {
        val db = readableDatabase
        return db.query(
            TABLE_BLOCKED_DOMAINS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_DOMAIN_NAME ASC"
        )
    }

    // Clear history
    fun clearHistory() {
        val db = writableDatabase
        db.delete(TABLE_HISTORY, null, null)
        db.close()
    }

    // Delete history entry by ID
    fun deleteHistoryEntry(id: Long) {
        val db = writableDatabase
        db.delete(TABLE_HISTORY, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
    }
}