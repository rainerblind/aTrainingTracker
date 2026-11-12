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
    val restingHrBpm: Int? = null,
    val rmssdMs: Double? = null,
    val lnRmssd: Double? = null,
    val hrvScore100: Double? = null,
    val sdnnMs: Double? = null,
    val artifactRatePct: Double? = null,
    val durationSec: Int? = null,
    val posture: HrvPosture,
    val isPrimaryForDay: Boolean = false,
    val notes: String? = null,
    val sleepDurationMin: Int? = null,
    val sleepQuality1To5: Int? = null,
    val morningEnergy1To5: Int? = null,
    val muscleSoreness1To5: Int? = null,
    val contextTags: String? = null
) {
    fun toContentValues(): ContentValues {
        val cv = ContentValues()
        if (id > 0) {
            cv.put(HrvReadinessDbHelper.COLUMN_ID, id)
        }
        cv.put(HrvReadinessDbHelper.COLUMN_TIMESTAMP_MS, timestampMs)
        cv.put(HrvReadinessDbHelper.COLUMN_DATE_ISO, dateIso)
        cv.put(HrvReadinessDbHelper.COLUMN_TIMEZONE_ID, timezoneId)
        if (restingHrBpm != null) {
            cv.put(HrvReadinessDbHelper.COLUMN_RESTING_HR_BPM, restingHrBpm)
        } else {
            cv.putNull(HrvReadinessDbHelper.COLUMN_RESTING_HR_BPM)
        }
        if (rmssdMs != null) {
            cv.put(HrvReadinessDbHelper.COLUMN_RMSSD_MS, rmssdMs)
        } else {
            cv.putNull(HrvReadinessDbHelper.COLUMN_RMSSD_MS)
        }
        if (lnRmssd != null) {
            cv.put(HrvReadinessDbHelper.COLUMN_LN_RMSSD, lnRmssd)
        } else {
            cv.putNull(HrvReadinessDbHelper.COLUMN_LN_RMSSD)
        }
        if (hrvScore100 != null) {
            cv.put(HrvReadinessDbHelper.COLUMN_HRV_SCORE_100, hrvScore100)
        } else {
            cv.putNull(HrvReadinessDbHelper.COLUMN_HRV_SCORE_100)
        }
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
        if (durationSec != null) {
            cv.put(HrvReadinessDbHelper.COLUMN_DURATION_SEC, durationSec)
        } else {
            cv.putNull(HrvReadinessDbHelper.COLUMN_DURATION_SEC)
        }
        cv.put(HrvReadinessDbHelper.COLUMN_POSTURE, posture.name)
        cv.put(HrvReadinessDbHelper.COLUMN_IS_PRIMARY_FOR_DAY, if (isPrimaryForDay) 1 else 0)
        if (notes != null) {
            cv.put(HrvReadinessDbHelper.COLUMN_NOTES, notes)
        } else {
            cv.putNull(HrvReadinessDbHelper.COLUMN_NOTES)
        }
        if (sleepDurationMin != null) {
            cv.put(HrvReadinessDbHelper.COLUMN_SLEEP_DURATION_MIN, sleepDurationMin)
        } else {
            cv.putNull(HrvReadinessDbHelper.COLUMN_SLEEP_DURATION_MIN)
        }
        if (sleepQuality1To5 != null) {
            cv.put(HrvReadinessDbHelper.COLUMN_SLEEP_QUALITY_1_5, sleepQuality1To5)
        } else {
            cv.putNull(HrvReadinessDbHelper.COLUMN_SLEEP_QUALITY_1_5)
        }
        if (morningEnergy1To5 != null) {
            cv.put(HrvReadinessDbHelper.COLUMN_MORNING_ENERGY_1_5, morningEnergy1To5)
        } else {
            cv.putNull(HrvReadinessDbHelper.COLUMN_MORNING_ENERGY_1_5)
        }
        if (muscleSoreness1To5 != null) {
            cv.put(HrvReadinessDbHelper.COLUMN_MUSCLE_SORENESS_1_5, muscleSoreness1To5)
        } else {
            cv.putNull(HrvReadinessDbHelper.COLUMN_MUSCLE_SORENESS_1_5)
        }
        if (contextTags != null) {
            cv.put(HrvReadinessDbHelper.COLUMN_CONTEXT_TAGS, contextTags)
        } else {
            cv.putNull(HrvReadinessDbHelper.COLUMN_CONTEXT_TAGS)
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

            val sleepDurIdx = cursor.getColumnIndex(HrvReadinessDbHelper.COLUMN_SLEEP_DURATION_MIN)
            val sleepQualIdx = cursor.getColumnIndex(HrvReadinessDbHelper.COLUMN_SLEEP_QUALITY_1_5)
            val energyIdx = cursor.getColumnIndex(HrvReadinessDbHelper.COLUMN_MORNING_ENERGY_1_5)
            val sorenessIdx = cursor.getColumnIndex(HrvReadinessDbHelper.COLUMN_MUSCLE_SORENESS_1_5)
            val tagsIdx = cursor.getColumnIndex(HrvReadinessDbHelper.COLUMN_CONTEXT_TAGS)

            val postureName = cursor.getString(postIdx)
            val posture = HrvPosture.fromNameOrNull(postureName)
                ?: HrvPosture.SUPINE

            val hr = if (cursor.isNull(hrIdx)) null else cursor.getInt(hrIdx)
            val rmssd = if (cursor.isNull(rmssdIdx)) null else cursor.getDouble(rmssdIdx)
            val ln = if (cursor.isNull(lnIdx)) null else cursor.getDouble(lnIdx)
            val score = if (cursor.isNull(scoreIdx)) null else cursor.getDouble(scoreIdx)
            val sdnn = if (cursor.isNull(sdnnIdx)) null else cursor.getDouble(sdnnIdx)
            val artRate = if (cursor.isNull(artIdx)) null else cursor.getDouble(artIdx)
            val dur = if (cursor.isNull(durIdx)) null else cursor.getInt(durIdx)
            val notes = if (cursor.isNull(notesIdx)) null else cursor.getString(notesIdx)

            val sleepDur = if (sleepDurIdx != -1 && !cursor.isNull(sleepDurIdx)) cursor.getInt(sleepDurIdx) else null
            val sleepQual = if (sleepQualIdx != -1 && !cursor.isNull(sleepQualIdx)) cursor.getInt(sleepQualIdx) else null
            val energy = if (energyIdx != -1 && !cursor.isNull(energyIdx)) cursor.getInt(energyIdx) else null
            val soreness = if (sorenessIdx != -1 && !cursor.isNull(sorenessIdx)) cursor.getInt(sorenessIdx) else null
            val tags = if (tagsIdx != -1 && !cursor.isNull(tagsIdx)) cursor.getString(tagsIdx) else null

            return HrvReadinessEntry(
                id = cursor.getLong(idIdx),
                timestampMs = cursor.getLong(tsIdx),
                dateIso = cursor.getString(dateIdx),
                timezoneId = cursor.getString(tzIdx),
                restingHrBpm = hr,
                rmssdMs = rmssd,
                lnRmssd = ln,
                hrvScore100 = score,
                sdnnMs = sdnn,
                artifactRatePct = artRate,
                durationSec = dur,
                posture = posture,
                isPrimaryForDay = cursor.getInt(primIdx) == 1,
                notes = notes,
                sleepDurationMin = sleepDur,
                sleepQuality1To5 = sleepQual,
                morningEnergy1To5 = energy,
                muscleSoreness1To5 = soreness,
                contextTags = tags
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

            val sleepDurIdx = cursor.getColumnIndex(HrvReadinessDbHelper.COLUMN_SLEEP_DURATION_MIN)
            val sleepQualIdx = cursor.getColumnIndex(HrvReadinessDbHelper.COLUMN_SLEEP_QUALITY_1_5)
            val energyIdx = cursor.getColumnIndex(HrvReadinessDbHelper.COLUMN_MORNING_ENERGY_1_5)
            val sorenessIdx = cursor.getColumnIndex(HrvReadinessDbHelper.COLUMN_MUSCLE_SORENESS_1_5)
            val tagsIdx = cursor.getColumnIndex(HrvReadinessDbHelper.COLUMN_CONTEXT_TAGS)

            do {
                val postureName = cursor.getString(postIdx)
                val posture = HrvPosture.fromNameOrNull(postureName) ?: HrvPosture.SUPINE
                val hr = if (cursor.isNull(hrIdx)) null else cursor.getInt(hrIdx)
                val rmssd = if (cursor.isNull(rmssdIdx)) null else cursor.getDouble(rmssdIdx)
                val ln = if (cursor.isNull(lnIdx)) null else cursor.getDouble(lnIdx)
                val score = if (cursor.isNull(scoreIdx)) null else cursor.getDouble(scoreIdx)
                val sdnn = if (cursor.isNull(sdnnIdx)) null else cursor.getDouble(sdnnIdx)
                val artRate = if (cursor.isNull(artIdx)) null else cursor.getDouble(artIdx)
                val dur = if (cursor.isNull(durIdx)) null else cursor.getInt(durIdx)
                val notes = if (cursor.isNull(notesIdx)) null else cursor.getString(notesIdx)

                val sleepDur = if (sleepDurIdx != -1 && !cursor.isNull(sleepDurIdx)) cursor.getInt(sleepDurIdx) else null
                val sleepQual = if (sleepQualIdx != -1 && !cursor.isNull(sleepQualIdx)) cursor.getInt(sleepQualIdx) else null
                val energy = if (energyIdx != -1 && !cursor.isNull(energyIdx)) cursor.getInt(energyIdx) else null
                val soreness = if (sorenessIdx != -1 && !cursor.isNull(sorenessIdx)) cursor.getInt(sorenessIdx) else null
                val tags = if (tagsIdx != -1 && !cursor.isNull(tagsIdx)) cursor.getString(tagsIdx) else null

                list.add(
                    HrvReadinessEntry(
                        id = cursor.getLong(idIdx),
                        timestampMs = cursor.getLong(tsIdx),
                        dateIso = cursor.getString(dateIdx),
                        timezoneId = cursor.getString(tzIdx),
                        restingHrBpm = hr,
                        rmssdMs = rmssd,
                        lnRmssd = ln,
                        hrvScore100 = score,
                        sdnnMs = sdnn,
                        artifactRatePct = artRate,
                        durationSec = dur,
                        posture = posture,
                        isPrimaryForDay = cursor.getInt(primIdx) == 1,
                        notes = notes,
                        sleepDurationMin = sleepDur,
                        sleepQuality1To5 = sleepQual,
                        morningEnergy1To5 = energy,
                        muscleSoreness1To5 = soreness,
                        contextTags = tags
                    )
                )
            } while (cursor.moveToNext())

            return list
        }
    }
}
