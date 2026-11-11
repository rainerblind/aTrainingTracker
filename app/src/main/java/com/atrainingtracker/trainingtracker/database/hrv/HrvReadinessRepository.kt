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
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.database.hrv

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Repository coordinating morning HRV readiness database persistence and Smallest Worthwhile Change (SWC)
 * autonomic readiness calculation (`REQ-PER-030.1`, `REQ-PER-030.4`).
 */
class HrvReadinessRepository(
    private val databaseManager: HrvReadinessDatabaseManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun insertEntry(entry: HrvReadinessEntry): Long = withContext(ioDispatcher) {
        databaseManager.insertEntry(entry)
    }

    suspend fun deleteEntry(id: Long): Boolean = withContext(ioDispatcher) {
        databaseManager.deleteEntry(id)
    }

    suspend fun setPrimaryReading(id: Long, isPrimary: Boolean = true): Boolean = withContext(ioDispatcher) {
        databaseManager.setPrimaryReading(id, isPrimary)
    }

    suspend fun getEntryById(id: Long): HrvReadinessEntry? = withContext(ioDispatcher) {
        databaseManager.getEntryById(id)
    }

    suspend fun getEntriesForDate(dateIso: String, posture: HrvPosture): List<HrvReadinessEntry> = withContext(ioDispatcher) {
        databaseManager.getEntriesForDate(dateIso, posture)
    }

    suspend fun getPrimaryEntryForDate(dateIso: String, posture: HrvPosture): HrvReadinessEntry? = withContext(ioDispatcher) {
        databaseManager.getPrimaryEntryForDate(dateIso, posture)
    }

    suspend fun getAllEntries(posture: HrvPosture? = null): List<HrvReadinessEntry> = withContext(ioDispatcher) {
        databaseManager.getAllEntries(posture)
    }

    suspend fun calculateReadiness(
        targetDateIso: String,
        posture: HrvPosture
    ): HrvReadinessResult = withContext(ioDispatcher) {
        val targetDate = LocalDate.parse(targetDateIso)
        val dMinus59Iso = targetDate.minusDays(59).toString()
        val entries = databaseManager.getPrimaryEntriesInWindow(posture, dMinus59Iso, targetDateIso)
        HrvReadinessCalculator.calculateReadiness(targetDateIso, posture, entries)
    }

    companion object {
        @Volatile
        private var instance: HrvReadinessRepository? = null

        @JvmStatic
        fun getInstance(context: Context): HrvReadinessRepository {
            return instance ?: synchronized(this) {
                instance ?: HrvReadinessRepository(
                    HrvReadinessDatabaseManager.getInstance(context.applicationContext)
                ).also { instance = it }
            }
        }

        @JvmStatic
        @VisibleForTesting
        fun resetForTesting(newInstance: HrvReadinessRepository? = null) {
            instance = newInstance
        }
    }
}
