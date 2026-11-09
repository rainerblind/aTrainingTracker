/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log

/**
 * Reconciles and merges duplicate equipment records across Equipment.db, Links,
 * SportTypeEquipmentLink, and WorkoutSummaries (REQ-SET-098.4, TST-SET-098.4, ATT-6215).
 */
object EquipmentReconciliationHelper {

    private const val TAG = "EquipmentReconcile"
    private val DEBUG = com.atrainingtracker.BuildConfig.DEBUG

    private data class EquipmentRow(
        val id: Long,
        val name: String?,
        val sportType: String?,
        val frameType: Int,
        val stravaName: String?,
        val stravaId: String?,
        val retired: Int
    )

    /**
     * Executes automatic deduplication across Equipment.db and cascades updates to
     * Links, SportTypeLinks.db, and WorkoutSummaries.db.
     *
     * @param context Application context.
     * @param equipmentDb Injected Equipment.db (optional; defaults to EquipmentDbHelper instance).
     * @param summariesDb Injected WorkoutSummaries.db (optional).
     * @param sportTypeDb Injected SportTypeLinks.db (optional).
     * @return Number of duplicate rows pruned.
     */
    @JvmStatic
    @JvmOverloads
    fun reconcileDuplicates(
        context: Context,
        equipmentDb: SQLiteDatabase? = null,
        summariesDb: SQLiteDatabase? = null,
        sportTypeDb: SQLiteDatabase? = null
    ): Int {
        val eDb = equipmentDb ?: try {
            EquipmentDbHelper.getInstance(context).writableDatabase
        } catch (e: Exception) {
            Log.w(TAG, "Could not obtain writable Equipment.db", e)
            return 0
        }

        val allRows = mutableListOf<EquipmentRow>()
        try {
            eDb.query(
                EquipmentDbHelper.EQUIPMENT,
                arrayOf(
                    EquipmentDbHelper.C_ID,
                    EquipmentDbHelper.NAME,
                    EquipmentDbHelper.SPORT_TYPE,
                    EquipmentDbHelper.FRAME_TYPE,
                    EquipmentDbHelper.STRAVA_NAME,
                    EquipmentDbHelper.STRAVA_ID,
                    EquipmentDbHelper.RETIRED
                ),
                null, null, null, null,
                "${EquipmentDbHelper.C_ID} ASC"
            ).use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(EquipmentDbHelper.C_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(EquipmentDbHelper.NAME)
                val sportIdx = cursor.getColumnIndexOrThrow(EquipmentDbHelper.SPORT_TYPE)
                val frameIdx = cursor.getColumnIndexOrThrow(EquipmentDbHelper.FRAME_TYPE)
                val sNameIdx = cursor.getColumnIndexOrThrow(EquipmentDbHelper.STRAVA_NAME)
                val sIdIdx = cursor.getColumnIndexOrThrow(EquipmentDbHelper.STRAVA_ID)
                val retiredIdx = cursor.getColumnIndexOrThrow(EquipmentDbHelper.RETIRED)

                while (cursor.moveToNext()) {
                    allRows.add(
                        EquipmentRow(
                            id = cursor.getLong(idIdx),
                            name = cursor.getString(nameIdx),
                            sportType = cursor.getString(sportIdx),
                            frameType = cursor.getInt(frameIdx),
                            stravaName = cursor.getString(sNameIdx),
                            stravaId = cursor.getString(sIdIdx),
                            retired = cursor.getInt(retiredIdx)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading Equipment rows for deduplication", e)
            return 0
        }

        if (allRows.size <= 1) {
            return 0
        }

        val groups = groupDuplicates(allRows)
        var totalPruned = 0

        val activeSummariesDb = summariesDb ?: try {
            WorkoutSummariesDatabaseManager.getInstance(context).database
        } catch (e: Exception) {
            Log.w(TAG, "Could not obtain WorkoutSummaries.db for cascade", e)
            null
        }

        val activeSportDb = sportTypeDb ?: try {
            SportTypeEquipmentLinkManager.getInstance(context).getDatabase()
        } catch (e: Exception) {
            Log.w(TAG, "Could not obtain SportTypeLinks.db for cascade", e)
            null
        }

        for (group in groups) {
            if (group.size <= 1) continue

            val canonicalRow = pickCanonicalRow(group)
            val canonicalId = canonicalRow.id
            val duplicateRows = group.filter { it.id != canonicalId }

            val consolidatedName = pickConsolidatedName(group)
            val consolidatedStravaId = pickConsolidatedStravaId(group)
            val consolidatedStravaName = pickConsolidatedStravaName(group, consolidatedStravaId)
            val consolidatedFrameType = group.map { it.frameType }.firstOrNull { it > 0 } ?: canonicalRow.frameType
            val authenticStravaRow = group.firstOrNull { it.stravaId != null && it.stravaId == consolidatedStravaId }
            val consolidatedRetired = if (authenticStravaRow != null && authenticStravaRow.retired == 1) {
                1
            } else if (group.any { it.retired == 0 }) {
                0
            } else {
                1
            }

            eDb.beginTransaction()
            try {
                // 1. Update canonical row with consolidated attributes
                val cv = ContentValues().apply {
                    put(EquipmentDbHelper.NAME, consolidatedName)
                    put(EquipmentDbHelper.FRAME_TYPE, consolidatedFrameType)
                    if (consolidatedStravaName != null) {
                        put(EquipmentDbHelper.STRAVA_NAME, consolidatedStravaName)
                    } else {
                        putNull(EquipmentDbHelper.STRAVA_NAME)
                    }
                    if (consolidatedStravaId != null) {
                        put(EquipmentDbHelper.STRAVA_ID, consolidatedStravaId)
                    } else {
                        putNull(EquipmentDbHelper.STRAVA_ID)
                    }
                    put(EquipmentDbHelper.RETIRED, consolidatedRetired)
                }
                eDb.update(EquipmentDbHelper.EQUIPMENT, cv, "${EquipmentDbHelper.C_ID} = ?", arrayOf(canonicalId.toString()))

                for (dup in duplicateRows) {
                    val dupId = dup.id

                    // 2. Cascade table 'Links' (Sensor Links) in Equipment.db
                    migrateEquipmentLinks(eDb, canonicalId, dupId)

                    // 3. Cascade table 'SportTypeEquipmentLink' in SportTypeLinks.db
                    activeSportDb?.let { sDb ->
                        migrateSportTypeLinks(sDb, canonicalId, dupId)
                    }

                    // 4. Cascade table 'WorkoutSummaries' in WorkoutSummaries.db
                    activeSummariesDb?.let { wDb ->
                        val updateValues = ContentValues().apply {
                            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.EQUIPMENT_ID, canonicalId)
                        }
                        wDb.update(
                            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                            updateValues,
                            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.EQUIPMENT_ID} = ?",
                            arrayOf(dupId.toString())
                        )
                    }

                    // 5. Delete duplicate equipment row
                    eDb.delete(EquipmentDbHelper.EQUIPMENT, "${EquipmentDbHelper.C_ID} = ?", arrayOf(dupId.toString()))
                    totalPruned++
                }

                eDb.setTransactionSuccessful()
            } catch (e: Exception) {
                Log.e(TAG, "Error consolidating duplicate group for canonicalId=$canonicalId", e)
            } finally {
                eDb.endTransaction()
            }
        }

        if (totalPruned > 0) {
            try {
                com.atrainingtracker.trainingtracker.repositories.EquipmentRepository.notifyEquipmentLinksChanged(null)
            } catch (_: Throwable) {
            }
        }

        return totalPruned
    }

    private fun normalizeSportCategory(sportType: String?, frameType: Int): String {
        return if (sportType.equals("BIKE", ignoreCase = true) || frameType in 1..4) {
            "BIKE"
        } else {
            "RUN"
        }
    }

    private fun groupDuplicates(rows: List<EquipmentRow>): List<List<EquipmentRow>> {
        val bySport = rows.groupBy { normalizeSportCategory(it.sportType, it.frameType) }
        val allClusters = mutableListOf<List<EquipmentRow>>()

        for ((_, sportRows) in bySport) {
            val visited = BooleanArray(sportRows.size)

            for (i in sportRows.indices) {
                if (visited[i]) continue
                val cluster = mutableListOf<EquipmentRow>()
                val queue = ArrayDeque<Int>()
                queue.add(i)
                visited[i] = true

                while (queue.isNotEmpty()) {
                    val curIdx = queue.removeFirst()
                    val curRow = sportRows[curIdx]
                    cluster.add(curRow)

                    for (j in sportRows.indices) {
                        if (!visited[j] && isMatchingEquipment(curRow, sportRows[j])) {
                            visited[j] = true
                            queue.add(j)
                        }
                    }
                }
                allClusters.add(cluster)
            }
        }
        return allClusters
    }

    private fun isMatchingEquipment(a: EquipmentRow, b: EquipmentRow): Boolean {
        val namesA = listOfNotNull(a.name?.trim(), a.stravaName?.trim()).filter { it.isNotBlank() }
        val namesB = listOfNotNull(b.name?.trim(), b.stravaName?.trim()).filter { it.isNotBlank() }

        for (na in namesA) {
            for (nb in namesB) {
                if (na.equals(nb, ignoreCase = true)) {
                    return true
                }
            }
        }
        val sidA = a.stravaId?.trim()
        val sidB = b.stravaId?.trim()
        if (sidA != null && namesB.any { it.equals(sidA, ignoreCase = true) }) return true
        if (sidB != null && namesA.any { it.equals(sidB, ignoreCase = true) }) return true

        return false
    }

    private fun pickCanonicalRow(group: List<EquipmentRow>): EquipmentRow {
        return group.minByOrNull { it.id } ?: group.first()
    }

    private fun pickConsolidatedName(group: List<EquipmentRow>): String {
        val candidateNames = group.flatMap { listOfNotNull(it.name?.trim(), it.stravaName?.trim()) }
            .filter { it.isNotBlank() }

        val sorted = candidateNames.sortedWith(
            compareByDescending<String> { s ->
                val hasUpper = s.any { it.isUpperCase() }
                val isAllUpper = s.all { !it.isLetter() || it.isUpperCase() }
                if (hasUpper && !isAllUpper) 2 else if (isAllUpper) 1 else 0
            }.thenBy { it.length }
        )
        return sorted.firstOrNull() ?: group.first().name ?: "Equipment"
    }

    private fun pickConsolidatedStravaId(group: List<EquipmentRow>): String? {
        val names = group.flatMap { listOfNotNull(it.name?.trim()?.lowercase(), it.stravaName?.trim()?.lowercase()) }.toSet()
        val validStravaIds = group.mapNotNull { it.stravaId?.trim() }
            .filter { it.isNotBlank() && it.lowercase() !in names }

        return validStravaIds.firstOrNull()
    }

    private fun pickConsolidatedStravaName(group: List<EquipmentRow>, authenticStravaId: String?): String? {
        if (authenticStravaId != null) {
            val matching = group.firstOrNull { it.stravaId?.trim() == authenticStravaId }
            if (matching?.stravaName?.isNotBlank() == true) {
                return matching.stravaName.trim()
            }
        }
        return group.mapNotNull { it.stravaName?.trim() }.firstOrNull { it.isNotBlank() }
    }

    private fun migrateEquipmentLinks(db: SQLiteDatabase, canonicalId: Long, duplicateId: Long) {
        try {
            val existingDeviceIds = mutableSetOf<Long>()
            db.query(
                EquipmentDbHelper.LINKS,
                arrayOf(EquipmentDbHelper.ANT_DEVICE_ID),
                "${EquipmentDbHelper.EQUIPMENT_ID} = ?",
                arrayOf(canonicalId.toString()),
                null, null, null
            ).use { cursor ->
                val devCol = cursor.getColumnIndexOrThrow(EquipmentDbHelper.ANT_DEVICE_ID)
                while (cursor.moveToNext()) {
                    existingDeviceIds.add(cursor.getLong(devCol))
                }
            }

            val dupDeviceIds = mutableListOf<Long>()
            db.query(
                EquipmentDbHelper.LINKS,
                arrayOf(EquipmentDbHelper.ANT_DEVICE_ID),
                "${EquipmentDbHelper.EQUIPMENT_ID} = ?",
                arrayOf(duplicateId.toString()),
                null, null, null
            ).use { cursor ->
                val devCol = cursor.getColumnIndexOrThrow(EquipmentDbHelper.ANT_DEVICE_ID)
                while (cursor.moveToNext()) {
                    dupDeviceIds.add(cursor.getLong(devCol))
                }
            }

            for (devId in dupDeviceIds) {
                if (devId !in existingDeviceIds) {
                    val cv = ContentValues().apply {
                        put(EquipmentDbHelper.EQUIPMENT_ID, canonicalId)
                        put(EquipmentDbHelper.ANT_DEVICE_ID, devId)
                    }
                    db.insert(EquipmentDbHelper.LINKS, null, cv)
                    existingDeviceIds.add(devId)
                }
            }
            db.delete(EquipmentDbHelper.LINKS, "${EquipmentDbHelper.EQUIPMENT_ID} = ?", arrayOf(duplicateId.toString()))
        } catch (e: Exception) {
            Log.w(TAG, "Failed migrating Links from $duplicateId to $canonicalId", e)
        }
    }

    private fun migrateSportTypeLinks(db: SQLiteDatabase, canonicalId: Long, duplicateId: Long) {
        try {
            val existingSportIds = mutableSetOf<Long>()
            db.rawQuery(
                "SELECT sport_type_id FROM SportTypeEquipmentLink WHERE equipment_id = ?",
                arrayOf(canonicalId.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    existingSportIds.add(cursor.getLong(0))
                }
            }

            val dupSportIds = mutableListOf<Long>()
            db.rawQuery(
                "SELECT sport_type_id FROM SportTypeEquipmentLink WHERE equipment_id = ?",
                arrayOf(duplicateId.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    dupSportIds.add(cursor.getLong(0))
                }
            }

            for (sId in dupSportIds) {
                if (sId !in existingSportIds) {
                    val cv = ContentValues().apply {
                        put("sport_type_id", sId)
                        put("equipment_id", canonicalId)
                    }
                    db.insertWithOnConflict("SportTypeEquipmentLink", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                    existingSportIds.add(sId)
                }
            }
            db.delete("SportTypeEquipmentLink", "equipment_id = ?", arrayOf(duplicateId.toString()))
        } catch (e: Exception) {
            Log.w(TAG, "Failed migrating SportTypeEquipmentLink from $duplicateId to $canonicalId", e)
        }
    }
}
