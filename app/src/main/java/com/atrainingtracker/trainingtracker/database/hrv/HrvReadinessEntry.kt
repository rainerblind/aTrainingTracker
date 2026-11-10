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

import android.content.ContentValues
import android.database.Cursor

/**
 * Immutable data transfer object representing a morning HRV readiness measurement (`REQ-PER-030.1`).
 *
 * Persisted in `HrvReadiness.db` (`hrv_readiness_entries`). Enforces symmetric single-item
 * ([fromCursor]) and batch ([fromCursorBatch]) cursor mappings per Rule 20.
 */
data class HrvReadinessEntry(
    val id: Long = 0L,
    val timestampMs: Long,
    val dateIso: String,
    val timezoneId: String,
    val restingHrBpm: Int,
    val rmssdMs: Double,
    val lnRmssd: Double,
    val hrvScore100: Double,
    val sdnnMs: Double? = null,
    val artifactRatePct: Double? = null,
    val durationSec: Int,
    val posture: HrvPosture,
    val isPrimaryForDay: Boolean = false,
    val notes: String? = null
) {
    fun toContentValues(): ContentValues {
        val cv = ContentValues()
        if (id > 0) {
            cv.put(HrvReadinessDbHelper.COLUMN_ID, id)
        }
        cv.put(HrvReadinessDbHelper.COLUMN_TIMESTAMP_MS, timestampMs)
        cv.put(HrvReadinessDbHelper.COLUMN_DATE_ISO, dateIso)
        cv.put(HrvReadinessDbHelper.COLUMN_TIMEZONE_ID, timezoneId)
        cv.put(HrvReadinessDbHelper.COLUMN_RESTING_HR_BPM, restingHrBpm)
        cv.put(HrvReadinessDbHelper.COLUMN_RMSSD_MS, rmssdMs)
        cv.put(HrvReadinessDbHelper.COLUMN_LN_RMSSD, lnRmssd)
        cv.put(HrvReadinessDbHelper.COLUMN_HRV_SCORE_100, hrvScore100)
        if (sdnnMs != null) {
            cv.put(HrvReadinessDbHelper.COLUMN_SDNN_MS, sdnnMs)
        } else {
            cv.putNull(HrvReadinessDbHelper.COLUMN_SDNN_MS)
        }
        if (artifactRatePct != null) {
            cv.put(HrvReadinessDbHelper.COLUMN_ARTIFACT_RATE_PCT, artifactRatePct)
        } else {
            cv.putNull(HrvReadinessDbHelper.COLUMN_ARTIFACT_RATE_PCT)
        }
        cv.put(HrvReadinessDbHelper.COLUMN_DURATION_SEC, durationSec)
        cv.put(HrvReadinessDbHelper.COLUMN_POSTURE, posture.name)
        cv.put(HrvReadinessDbHelper.COLUMN_IS_PRIMARY_FOR_DAY, if (isPrimaryForDay) 1 else 0)
        if (notes != null) {
            cv.put(HrvReadinessDbHelper.COLUMN_NOTES, notes)
        } else {
            cv.putNull(HrvReadinessDbHelper.COLUMN_NOTES)
        }
        return cv
    }

    companion object {
        /**
         * Extracts a single [HrvReadinessEntry] from the current row of [cursor] without advancing or closing it (Rule 20).
         */
        @JvmStatic
        fun fromCursor(cursor: Cursor): HrvReadinessEntry {
            val idIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_ID)
            val tsIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_TIMESTAMP_MS)
            val dateIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_DATE_ISO)
            val tzIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_TIMEZONE_ID)
            val hrIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_RESTING_HR_BPM)
            val rmssdIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_RMSSD_MS)
            val lnIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_LN_RMSSD)
            val scoreIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_HRV_SCORE_100)
            val sdnnIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_SDNN_MS)
            val artIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_ARTIFACT_RATE_PCT)
            val durIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_DURATION_SEC)
            val postIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_POSTURE)
            val primIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_IS_PRIMARY_FOR_DAY)
            val notesIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_NOTES)

            val postureName = cursor.getString(postIdx)
            val posture = HrvPosture.fromNameOrNull(postureName)
                ?: HrvPosture.SUPINE

            val sdnn = if (cursor.isNull(sdnnIdx)) null else cursor.getDouble(sdnnIdx)
            val artRate = if (cursor.isNull(artIdx)) null else cursor.getDouble(artIdx)
            val notes = if (cursor.isNull(notesIdx)) null else cursor.getString(notesIdx)

            return HrvReadinessEntry(
                id = cursor.getLong(idIdx),
                timestampMs = cursor.getLong(tsIdx),
                dateIso = cursor.getString(dateIdx),
                timezoneId = cursor.getString(tzIdx),
                restingHrBpm = cursor.getInt(hrIdx),
                rmssdMs = cursor.getDouble(rmssdIdx),
                lnRmssd = cursor.getDouble(lnIdx),
                hrvScore100 = cursor.getDouble(scoreIdx),
                sdnnMs = sdnn,
                artifactRatePct = artRate,
                durationSec = cursor.getInt(durIdx),
                posture = posture,
                isPrimaryForDay = cursor.getInt(primIdx) == 1,
                notes = notes
            )
        }

        /**
         * Extracts all rows from [cursor] into a list of [HrvReadinessEntry] using identical field mapping logic (Rule 20).
         */
        @JvmStatic
        fun fromCursorBatch(cursor: Cursor): List<HrvReadinessEntry> {
            val list = ArrayList<HrvReadinessEntry>(cursor.count.coerceAtLeast(0))
            if (!cursor.moveToFirst()) return list

            val idIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_ID)
            val tsIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_TIMESTAMP_MS)
            val dateIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_DATE_ISO)
            val tzIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_TIMEZONE_ID)
            val hrIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_RESTING_HR_BPM)
            val rmssdIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_RMSSD_MS)
            val lnIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_LN_RMSSD)
            val scoreIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_HRV_SCORE_100)
            val sdnnIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_SDNN_MS)
            val artIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_ARTIFACT_RATE_PCT)
            val durIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_DURATION_SEC)
            val postIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_POSTURE)
            val primIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_IS_PRIMARY_FOR_DAY)
            val notesIdx = cursor.getColumnIndexOrThrow(HrvReadinessDbHelper.COLUMN_NOTES)

            do {
                val postureName = cursor.getString(postIdx)
                val posture = HrvPosture.fromNameOrNull(postureName) ?: HrvPosture.SUPINE
                val sdnn = if (cursor.isNull(sdnnIdx)) null else cursor.getDouble(sdnnIdx)
                val artRate = if (cursor.isNull(artIdx)) null else cursor.getDouble(artIdx)
                val notes = if (cursor.isNull(notesIdx)) null else cursor.getString(notesIdx)

                list.add(
                    HrvReadinessEntry(
                        id = cursor.getLong(idIdx),
                        timestampMs = cursor.getLong(tsIdx),
                        dateIso = cursor.getString(dateIdx),
                        timezoneId = cursor.getString(tzIdx),
                        restingHrBpm = cursor.getInt(hrIdx),
                        rmssdMs = cursor.getDouble(rmssdIdx),
                        lnRmssd = cursor.getDouble(lnIdx),
                        hrvScore100 = cursor.getDouble(scoreIdx),
                        sdnnMs = sdnn,
                        artifactRatePct = artRate,
                        durationSec = cursor.getInt(durIdx),
                        posture = posture,
                        isPrimaryForDay = cursor.getInt(primIdx) == 1,
                        notes = notes
                    )
                )
            } while (cursor.moveToNext())

            return list
        }
    }
}
