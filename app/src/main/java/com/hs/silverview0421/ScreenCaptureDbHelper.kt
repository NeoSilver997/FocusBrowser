package com.hs.silverview0421

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ScreenCaptureDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "screen_captures.db"
        private const val DATABASE_VERSION = 5

        // Screen captures table
        const val TABLE_CAPTURES = "screen_captures"
        const val COLUMN_ID = "id"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_IMAGE_DATA = "image_data"
        const val COLUMN_IMAGE_HASH = "image_hash"
        const val COLUMN_LAST_VIEW_TIME = "last_view_time"
        const val COLUMN_URL = "url"
        const val COLUMN_TITLE = "title"
        const val COLUMN_DOMAIN = "domain"
        
        // Password table
        private const val TABLE_PASSWORD = "password"
        private const val COLUMN_PASSWORD = "password_hash"
        private const val COLUMN_PASSWORD_SET = "is_set"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create screen captures table
        val createCapturesTable = "CREATE TABLE $TABLE_CAPTURES ("
                .plus("$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, ")
                .plus("$COLUMN_TIMESTAMP INTEGER NOT NULL, ")
                .plus("$COLUMN_IMAGE_DATA BLOB NOT NULL, ")
                .plus("$COLUMN_IMAGE_HASH TEXT, ")
                .plus("$COLUMN_LAST_VIEW_TIME INTEGER, ")
                .plus("$COLUMN_URL TEXT, ")
                .plus("$COLUMN_TITLE TEXT, ")
                .plus("$COLUMN_DOMAIN TEXT)")

        // Create password table
        val createPasswordTable = "CREATE TABLE $TABLE_PASSWORD ("
                .plus("$COLUMN_PASSWORD TEXT, ")
                .plus("$COLUMN_PASSWORD_SET INTEGER DEFAULT 0)")

        db.execSQL(createCapturesTable)
        db.execSQL(createPasswordTable)
        
        // Insert default row for password settings
        val values = ContentValues().apply {
            put(COLUMN_PASSWORD_SET, 0)
        }
        db.insert(TABLE_PASSWORD, null, values)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Create password table if upgrading from version 1
            val createPasswordTable = "CREATE TABLE $TABLE_PASSWORD ("
                    .plus("$COLUMN_PASSWORD TEXT, ")
                    .plus("$COLUMN_PASSWORD_SET INTEGER DEFAULT 0)")
            
            db.execSQL(createPasswordTable)
            
            // Insert default row for password settings
            val values = ContentValues().apply {
                put(COLUMN_PASSWORD_SET, 0)
            }
            db.insert(TABLE_PASSWORD, null, values)
        }
        
        if (oldVersion < 3) {
            // Add image_hash column to screen_captures table
            db.execSQL("ALTER TABLE $TABLE_CAPTURES ADD COLUMN $COLUMN_IMAGE_HASH TEXT")
        }
        
        if (oldVersion < 4) {
            // Add last_view_time column to screen_captures table
            db.execSQL("ALTER TABLE $TABLE_CAPTURES ADD COLUMN $COLUMN_LAST_VIEW_TIME INTEGER")
        }
        
        if (oldVersion < 5) {
            // Add browsing history related columns
            db.execSQL("ALTER TABLE $TABLE_CAPTURES ADD COLUMN $COLUMN_URL TEXT")
            db.execSQL("ALTER TABLE $TABLE_CAPTURES ADD COLUMN $COLUMN_TITLE TEXT")
            db.execSQL("ALTER TABLE $TABLE_CAPTURES ADD COLUMN $COLUMN_DOMAIN TEXT")
        }
    }

    // Add a screen capture to the database
    fun addScreenCapture(imageData: ByteArray, imageHash: String, url: String? = null, title: String? = null, domain: String? = null) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
            put(COLUMN_IMAGE_DATA, imageData)
            put(COLUMN_IMAGE_HASH, imageHash)
            put(COLUMN_LAST_VIEW_TIME, System.currentTimeMillis())
            put(COLUMN_URL, url)
            put(COLUMN_TITLE, title)
            put(COLUMN_DOMAIN, domain)
        }
        db.insert(TABLE_CAPTURES, null, values)
        db.close()
    }

    // Get all screen captures ordered by timestamp (newest first)
    fun getAllCaptures(): Cursor {
        val db = readableDatabase
        return db.query(
            TABLE_CAPTURES,
            arrayOf("$COLUMN_ID AS _id", COLUMN_TIMESTAMP, COLUMN_IMAGE_DATA, COLUMN_IMAGE_HASH, 
                   COLUMN_LAST_VIEW_TIME, COLUMN_URL, COLUMN_TITLE, COLUMN_DOMAIN),
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )
    }

    // Get screen captures from a specific date range
    fun getCapturesInRange(startTime: Long, endTime: Long): Cursor {
        val db = readableDatabase
        return db.query(
            TABLE_CAPTURES,
            arrayOf("$COLUMN_ID AS _id", COLUMN_TIMESTAMP, COLUMN_IMAGE_DATA, COLUMN_IMAGE_HASH, 
                   COLUMN_LAST_VIEW_TIME, COLUMN_URL, COLUMN_TITLE, COLUMN_DOMAIN),
            "$COLUMN_TIMESTAMP BETWEEN ? AND ?",
            arrayOf(startTime.toString(), endTime.toString()),
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )
    }

    // Delete captures older than the specified timestamp
    fun deleteOldCaptures(cutoffTime: Long): Int {
        val db = writableDatabase
        val deletedRows = db.delete(
            TABLE_CAPTURES,
            "$COLUMN_TIMESTAMP < ?",
            arrayOf(cutoffTime.toString())
        )
        db.close()
        return deletedRows
    }

    // Delete a specific capture by ID
    fun deleteCapture(id: Long): Int {
        val db = writableDatabase
        val deletedRows = db.delete(
            TABLE_CAPTURES,
            "$COLUMN_ID = ?",
            arrayOf(id.toString())
        )
        db.close()
        return deletedRows
    }
    
    // Get a specific capture by ID
    fun getCaptureById(id: Long): Cursor {
        val db = readableDatabase
        return db.query(
            TABLE_CAPTURES,
            arrayOf("$COLUMN_ID AS _id", COLUMN_TIMESTAMP, COLUMN_IMAGE_DATA, COLUMN_IMAGE_HASH, 
                   COLUMN_LAST_VIEW_TIME, COLUMN_URL, COLUMN_TITLE, COLUMN_DOMAIN),
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
    }
    
    // Update the last view time for a capture
    fun updateLastViewTime(id: Long) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_LAST_VIEW_TIME, System.currentTimeMillis())
        }
        db.update(
            TABLE_CAPTURES,
            values,
            "$COLUMN_ID = ?",
            arrayOf(id.toString())
        )
        db.close()
    }

    // Get the total number of captures stored
    fun getCaptureCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_CAPTURES", null)
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        return count
    }
    
    // Get captures by domain
    fun getCapturesByDomain(domain: String): Cursor {
        val db = readableDatabase
        return db.query(
            TABLE_CAPTURES,
            arrayOf("$COLUMN_ID AS _id", COLUMN_TIMESTAMP, COLUMN_IMAGE_DATA, COLUMN_IMAGE_HASH, 
                   COLUMN_LAST_VIEW_TIME, COLUMN_URL, COLUMN_TITLE, COLUMN_DOMAIN),
            "$COLUMN_DOMAIN = ?",
            arrayOf(domain),
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )
    }
    
    // Get all unique domains from captures
    fun getAllDomains(): List<String> {
        val domains = mutableListOf<String>()
        val db = readableDatabase
        val cursor = db.query(
            true, // distinct
            TABLE_CAPTURES,
            arrayOf(COLUMN_DOMAIN),
            "$COLUMN_DOMAIN IS NOT NULL",
            null,
            null,
            null,
            "$COLUMN_DOMAIN ASC",
            null
        )
        
        cursor.use {
            while (it.moveToNext()) {
                val domainIndex = it.getColumnIndex(COLUMN_DOMAIN)
                if (domainIndex >= 0 && !it.isNull(domainIndex)) {
                    domains.add(it.getString(domainIndex))
                }
            }
        }
        
        return domains
    }
    
    // Clear all captures from the database
    fun clearAllCaptures(): Int {
        val db = writableDatabase
        val deletedRows = db.delete(TABLE_CAPTURES, null, null)
        db.close()
        return deletedRows
    }
    
    // Set password for screen captures
    fun setPassword(password: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PASSWORD, password)
            put(COLUMN_PASSWORD_SET, 1)
        }
        db.update(TABLE_PASSWORD, values, null, null)
        db.close()
    }
    
    // Check if password is set
    fun isPasswordSet(): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PASSWORD,
            arrayOf(COLUMN_PASSWORD_SET),
            null,
            null,
            null,
            null,
            null
        )
        var isSet = false
        if (cursor.moveToFirst()) {
            isSet = cursor.getInt(0) == 1
        }
        cursor.close()
        return isSet
    }
    
    // Verify password
    fun verifyPassword(password: String): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PASSWORD,
            arrayOf(COLUMN_PASSWORD),
            "$COLUMN_PASSWORD_SET = 1",
            null,
            null,
            null,
            null
        )
        var isCorrect = false
        if (cursor.moveToFirst()) {
            val storedPassword = cursor.getString(0)
            isCorrect = password == storedPassword
        }
        cursor.close()
        return isCorrect
    }
    
    // Remove password protection
    fun removePassword() {
        val db = writableDatabase
        val values = ContentValues().apply {
            putNull(COLUMN_PASSWORD)
            put(COLUMN_PASSWORD_SET, 0)
        }
        db.update(TABLE_PASSWORD, values, null, null)
        db.close()
    }
    
    // Get the most recent capture hash
    fun getLastCaptureHash(): String? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CAPTURES,
            arrayOf(COLUMN_IMAGE_HASH),
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC",
            "1"
        )
        
        var hash: String? = null
        if (cursor.moveToFirst()) {
            val hashIndex = cursor.getColumnIndex(COLUMN_IMAGE_HASH)
            if (hashIndex >= 0) {
                hash = cursor.getString(hashIndex)
            }
        }
        cursor.close()
        return hash
    }
    
    // Get the most recent capture ID
    fun getLastCaptureId(): Long? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CAPTURES,
            arrayOf(COLUMN_ID),
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC",
            "1"
        )
        
        var id: Long? = null
        if (cursor.moveToFirst()) {
            val idIndex = cursor.getColumnIndex(COLUMN_ID)
            if (idIndex >= 0) {
                id = cursor.getLong(idIndex)
            }
        }
        cursor.close()
        return id
    }
}