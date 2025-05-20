package com.hs.silverview0421

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ScreenCaptureDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "screen_captures.db"
        private const val DATABASE_VERSION = 2

        // Screen captures table
        const val TABLE_CAPTURES = "screen_captures"
        const val COLUMN_ID = "id"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_IMAGE_DATA = "image_data"
        
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
                .plus("$COLUMN_IMAGE_DATA BLOB NOT NULL)")

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
    }

    // Add a screen capture to the database
    fun addScreenCapture(imageData: ByteArray) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
            put(COLUMN_IMAGE_DATA, imageData)
        }
        db.insert(TABLE_CAPTURES, null, values)
        db.close()
    }

    // Get all screen captures ordered by timestamp (newest first)
    fun getAllCaptures(): Cursor {
        val db = readableDatabase
        return db.query(
            TABLE_CAPTURES,
            arrayOf("$COLUMN_ID AS _id", COLUMN_TIMESTAMP, COLUMN_IMAGE_DATA),
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
            arrayOf("$COLUMN_ID AS _id", COLUMN_TIMESTAMP, COLUMN_IMAGE_DATA),
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
            arrayOf("$COLUMN_ID AS _id", COLUMN_TIMESTAMP, COLUMN_IMAGE_DATA),
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
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
}