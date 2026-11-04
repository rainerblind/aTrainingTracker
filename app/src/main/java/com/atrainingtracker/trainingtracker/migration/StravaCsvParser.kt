/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import com.atrainingtracker.banalservice.BSportType
import java.io.BufferedReader
import java.io.Reader
import java.util.Locale

/**
 * Data transfer model representing a single activity row parsed from Strava's `activities.csv`.
 *
 * Traceability:
 * - REQ-MIG-048.2: Bilingual & Multi-Line RFC 4180 CSV Metadata Parser.
 * - TST-MIG-046.1: Bilingual & Multi-Line RFC 4180 CSV Metadata Parser Verification.
 */
data class StravaActivityRow(
    val activityId: String,
    val activityDate: String? = null,
    val activityName: String? = null,
    val activityType: String? = null,
    val bSportType: BSportType = BSportType.UNKNOWN,
    val activityDescription: String? = null,
    val privateNote: String? = null,
    val commute: Boolean = false,
    val gearName: String? = null,
    val filename: String? = null,
    val elapsedTime: Double? = null,
    val movingTime: Double? = null,
    val distance: Double? = null,
    val elevationGain: Double? = null,
    val calories: Double? = null,
    val avgHr: Double? = null,
    val maxHr: Double? = null,
    val avgWatts: Double? = null,
    val maxWatts: Double? = null
)

/**
 * Data model representing equipment entries parsed from Strava's `bikes.csv` or `shoes.csv`.
 */
data class StravaGearItem(
    val name: String,
    val brand: String? = null,
    val model: String? = null,
    val isBike: Boolean = true
)

/**
 * Streaming RFC 4180 compliant CSV parser supporting German/English header aliases,
 * multi-line quoted fields, escaped quotes, and Strava metadata extraction (REQ-MIG-048.2).
 */
object StravaCsvParser {

    /**
     * Parses arbitrary CSV content adhering to RFC 4180 (handling newlines in quotes and escaped `""`).
     */
    fun parseCsv(reader: Reader): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val currentField = StringBuilder()
        val currentRecord = mutableListOf<String>()
        var inQuotes = false

        val br = if (reader is BufferedReader) reader else BufferedReader(reader)
        var chInt = br.read()
        while (chInt != -1) {
            val ch = chInt.toChar()
            when {
                ch == '\"' -> {
                    if (inQuotes) {
                        br.mark(1)
                        val nextInt = br.read()
                        if (nextInt != -1 && nextInt.toChar() == '\"') {
                            currentField.append('\"')
                        } else {
                            br.reset()
                            inQuotes = false
                        }
                    } else {
                        inQuotes = true
                    }
                }
                ch == ',' && !inQuotes -> {
                    currentRecord.add(currentField.toString())
                    currentField.setLength(0)
                }
                (ch == '\r' || ch == '\n') && !inQuotes -> {
                    if (ch == '\r') {
                        br.mark(1)
                        val nextInt = br.read()
                        if (nextInt != -1 && nextInt.toChar() != '\n') {
                            br.reset()
                        }
                    }
                    currentRecord.add(currentField.toString())
                    currentField.setLength(0)
                    if (currentRecord.isNotEmpty() && (currentRecord.size > 1 || currentRecord[0].isNotBlank())) {
                        records.add(ArrayList(currentRecord))
                    }
                    currentRecord.clear()
                }
                else -> {
                    currentField.append(ch)
                }
            }
            chInt = br.read()
        }
        if (currentField.isNotEmpty() || currentRecord.isNotEmpty()) {
            currentRecord.add(currentField.toString())
            if (currentRecord.isNotEmpty() && (currentRecord.size > 1 || currentRecord[0].isNotBlank())) {
                records.add(currentRecord)
            }
        }
        return records
    }

    /**
     * Parses `activities.csv` into a list of [StravaActivityRow], handling both English and German header names.
     */
    fun parseActivities(reader: Reader): List<StravaActivityRow> {
        val records = parseCsv(reader)
        if (records.isEmpty()) return emptyList()

        val headers = records[0].map { normalize(it) }
        val idIdx = findFirstHeader(headers, "aktivitäts-id", "activity id", "aktivitaets-id")
        val dateIdx = findFirstHeader(headers, "aktivitätsdatum", "activity date", "aktivitaetsdatum")
        val nameIdx = findFirstHeader(headers, "name der aktivität", "activity name", "name der aktivitaet", "name")
        val typeIdx = findFirstHeader(headers, "aktivitätsart", "activity type", "aktivitaetsart", "sport", "type")
        val descIdx = findFirstHeader(headers, "aktivitätsbeschreibung", "activity description", "aktivitaetsbeschreibung", "description")
        val noteIdx = findFirstHeader(headers, "hinweis zur privatsphäre für aktivitäten", "activity private note", "private note", "hinweis zur privatsphaere fuer aktivitaeten")
        val commuteIdx = findFirstHeader(headers, "pendeln", "commute")
        val gearIdx = findFirstHeader(headers, "aktivitätsausrüstung", "activity gear", "gear", "ausrüstung", "fahrrad", "aktivitaetsausruestung")
        val filenameIdx = findFirstHeader(headers, "dateiname", "filename", "file name")
        val elapsedTimeIdx = findFirstHeader(headers, "verstrichene zeit", "elapsed time")
        val movingTimeIdx = findFirstHeader(headers, "bewegungszeit", "moving time")
        val distanceIdx = findFirstHeader(headers, "distanz", "distance")
        val elevIdx = findFirstHeader(headers, "höhenzunahme", "elevation gain", "hoehenzunahme")
        val calIdx = findFirstHeader(headers, "kalorien", "calories")
        val avgHrIdx = findFirstHeader(headers, "durchschnittliche herzfrequenz", "average heart rate")
        val maxHrIdx = findFirstHeader(headers, "max. herzfrequenz", "max heart rate")
        val avgWattsIdx = findFirstHeader(headers, "durchschnittliche watt", "average watts")
        val maxWattsIdx = findFirstHeader(headers, "max. watt", "max watts")

        val result = mutableListOf<StravaActivityRow>()
        for (i in 1 until records.size) {
            val row = records[i]
            val rawId = getField(row, idIdx)
            if (rawId.isNullOrBlank()) continue

            val rawType = getField(row, typeIdx)
            val sportType = mapSportType(rawType)

            result.add(
                StravaActivityRow(
                    activityId = rawId.trim(),
                    activityDate = getField(row, dateIdx)?.trim(),
                    activityName = getField(row, nameIdx)?.trim(),
                    activityType = rawType?.trim(),
                    bSportType = sportType,
                    activityDescription = getField(row, descIdx)?.trim(),
                    privateNote = getField(row, noteIdx)?.trim(),
                    commute = parseBoolean(getField(row, commuteIdx)),
                    gearName = getField(row, gearIdx)?.trim(),
                    filename = getField(row, filenameIdx)?.trim()?.replace('\\', '/'),
                    elapsedTime = parseDouble(getField(row, elapsedTimeIdx)),
                    movingTime = parseDouble(getField(row, movingTimeIdx)),
                    distance = parseDouble(getField(row, distanceIdx)),
                    elevationGain = parseDouble(getField(row, elevIdx)),
                    calories = parseDouble(getField(row, calIdx)),
                    avgHr = parseDouble(getField(row, avgHrIdx)),
                    maxHr = parseDouble(getField(row, maxHrIdx)),
                    avgWatts = parseDouble(getField(row, avgWattsIdx)),
                    maxWatts = parseDouble(getField(row, maxWattsIdx))
                )
            )
        }
        return result
    }

    /**
     * Parses `bikes.csv` into a list of [StravaGearItem].
     */
    fun parseBikes(reader: Reader): List<StravaGearItem> {
        val records = parseCsv(reader)
        if (records.isEmpty()) return emptyList()

        val headers = records[0].map { normalize(it) }
        val nameIdx = findFirstHeader(headers, "fahrradname", "bike name", "name")
        val brandIdx = findFirstHeader(headers, "fahrradmarke", "bike brand", "brand")
        val modelIdx = findFirstHeader(headers, "fahrradmodell", "bike model", "model")

        val list = mutableListOf<StravaGearItem>()
        for (i in 1 until records.size) {
            val row = records[i]
            val name = getField(row, nameIdx)?.trim()
            val brand = getField(row, brandIdx)?.trim()
            val model = getField(row, modelIdx)?.trim()
            val displayName = when {
                !name.isNullOrBlank() -> name
                !brand.isNullOrBlank() && !model.isNullOrBlank() -> "$brand $model"
                !brand.isNullOrBlank() -> brand
                !model.isNullOrBlank() -> model
                else -> null
            }
            if (!displayName.isNullOrBlank()) {
                list.add(StravaGearItem(name = displayName, brand = brand, model = model, isBike = true))
            }
        }
        return list
    }

    /**
     * Parses `shoes.csv` into a list of [StravaGearItem].
     */
    fun parseShoes(reader: Reader): List<StravaGearItem> {
        val records = parseCsv(reader)
        if (records.isEmpty()) return emptyList()

        val headers = records[0].map { normalize(it) }
        val nameIdx = findFirstHeader(headers, "schuhname", "shoe name", "name")
        val brandIdx = findFirstHeader(headers, "schuhmarke", "shoe brand", "brand")
        val modelIdx = findFirstHeader(headers, "schuhmodell", "shoe model", "model")

        val list = mutableListOf<StravaGearItem>()
        for (i in 1 until records.size) {
            val row = records[i]
            val name = getField(row, nameIdx)?.trim()
            val brand = getField(row, brandIdx)?.trim()
            val model = getField(row, modelIdx)?.trim()
            val displayName = when {
                !name.isNullOrBlank() -> name
                !brand.isNullOrBlank() && !model.isNullOrBlank() -> "$brand $model"
                !brand.isNullOrBlank() -> brand
                !model.isNullOrBlank() -> model
                else -> null
            }
            if (!displayName.isNullOrBlank()) {
                list.add(StravaGearItem(name = displayName, brand = brand, model = model, isBike = false))
            }
        }
        return list
    }

    /**
     * Maps localized Strava activity type strings to canonical [BSportType].
     */
    fun mapSportType(type: String?): BSportType {
        if (type.isNullOrBlank()) return BSportType.UNKNOWN
        val lower = type.trim().lowercase(Locale.ROOT)
        return when {
            lower.contains("ride") || lower.contains("radfahrt") || lower.contains("cycling") ||
                    lower.contains("bike") || lower.contains("velomobile") -> BSportType.BIKE
            lower.contains("run") || lower.contains("lauf") || lower.contains("walk") ||
                    lower.contains("spaziergang") || lower.contains("hike") || lower.contains("wander") -> BSportType.RUN
            else -> BSportType.UNKNOWN
        }
    }

    private fun normalize(str: String): String {
        return str.trim().removePrefix("\uFEFF").lowercase(Locale.ROOT)
    }

    private fun findFirstHeader(headers: List<String>, vararg aliases: String): Int {
        for (alias in aliases) {
            val idx = headers.indexOf(alias)
            if (idx != -1) return idx
        }
        return -1
    }

    private fun getField(row: List<String>, index: Int): String? {
        if (index < 0 || index >= row.size) return null
        val v = row[index]
        return if (v.isNotBlank()) v else null
    }

    private fun parseBoolean(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val clean = value.trim().lowercase(Locale.ROOT)
        return clean == "true" || clean == "1" || clean == "ja" || clean == "yes"
    }

    private fun parseDouble(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        return try {
            value.trim().replace(',', '.').toDouble()
        } catch (_: NumberFormatException) {
            null
        }
    }
}
