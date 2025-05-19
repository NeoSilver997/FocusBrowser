package com.hs.silverview0421

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ScreenCaptureDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "screen_captures.db"
        private const val DATABASE_VERSION = 1

        // Screen captures table
        const val TABLE_CAPTURES = "screen_captures"
        const val COLUMN_ID = "id"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_IMAGE_DATA = "image_data"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create screen captures table
        val createCapturesTable = "CREATE TABLE $TABLE_CAPTURES ("
                .plus("$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, ")
                .plus("$COLUMN_TIMESTAMP INTEGER NOT NULL, ")
                .plus("$COLUMN_IMAGE_DATA BLOB NOT NULL)")

        db.execSQL(createCapturesTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle database upgrades if needed
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CAPTURES")
        onCreate(db)
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

    // Get the total number of captures stored
    fun getCaptureCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_CAPTURES", null)
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        return count
    }
}