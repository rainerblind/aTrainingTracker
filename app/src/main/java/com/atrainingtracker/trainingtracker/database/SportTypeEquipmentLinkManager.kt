package com.atrainingtracker.trainingtracker.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction

class SportTypeEquipmentLinkManager private constructor(context: Context) {

    // Internal Helper to manage the database file
    private class LinkDbHelper(context: Context) : SQLiteOpenHelper(context, "SportTypeLinks.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(CREATE_TABLE_SQL)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Future migrations handled here
        }
    }

    private val dbHelper = LinkDbHelper(context)
    private val db: SQLiteDatabase get() = dbHelper.writableDatabase

    companion object {
        private const val TABLE_NAME = "SportTypeEquipmentLink"
        private const val COLUMN_SPORT_ID = "sport_type_id"
        private const val COLUMN_EQUIP_ID = "equipment_id"

        private const val CREATE_TABLE_SQL = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_SPORT_ID INTEGER,
                $COLUMN_EQUIP_ID INTEGER,
                PRIMARY KEY ($COLUMN_SPORT_ID, $COLUMN_EQUIP_ID)
            )
        """

        @Volatile
        private var instance: SportTypeEquipmentLinkManager? = null

        fun getInstance(context: Context): SportTypeEquipmentLinkManager {
            return instance ?: synchronized(this) {
                instance ?: SportTypeEquipmentLinkManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Replaces all equipment links for a specific Sport Type.
     */
    fun updateLinksForSportType(sportId: Long, equipmentIds: List<Long>) {
        db.transaction {
            try {
                delete(TABLE_NAME, "$COLUMN_SPORT_ID = ?", arrayOf(sportId.toString()))
                equipmentIds.forEach { equipId ->
                    val cv = ContentValues().apply {
                        put(COLUMN_SPORT_ID, sportId)
                        put(COLUMN_EQUIP_ID, equipId)
                    }
                    insert(TABLE_NAME, null, cv)
                }
            } finally {
            }
        }
    }

    /**
     * Replaces all sport type links for a specific piece of Equipment.
     */
    fun updateLinksForEquipment(equipmentId: Long, sportTypeIds: List<Long>) {
        db.transaction {
            try {
                delete(TABLE_NAME, "$COLUMN_EQUIP_ID = ?", arrayOf(equipmentId.toString()))
                sportTypeIds.forEach { sportId ->
                    val cv = ContentValues().apply {
                        put(COLUMN_SPORT_ID, sportId)
                        put(COLUMN_EQUIP_ID, equipmentId)
                    }
                    insert(TABLE_NAME, null, cv)
                }
            } finally {
            }
        }
    }

    /**
     * Returns the IDs of all equipment linked to a sport.
     */
    fun getEquipmentIdsForSport(sportId: Long): List<Long> {
        val ids = mutableListOf<Long>()
        db.query(
            TABLE_NAME, arrayOf(COLUMN_EQUIP_ID),
            "$COLUMN_SPORT_ID = ?", arrayOf(sportId.toString()),
            null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(0))
            }
        }
        return ids
    }

    /**
     * Returns the IDs of all sports linked to an equipment.
     */
    fun getSportTypeIdsForEquipment(equipmentId: Long): List<Long> {
        val ids = mutableListOf<Long>()
        db.query(
            TABLE_NAME, arrayOf(COLUMN_SPORT_ID),
            "$COLUMN_EQUIP_ID = ?", arrayOf(equipmentId.toString()),
            null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(0))
            }
        }
        return ids
    }
}