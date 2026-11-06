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
import java.io.File
import java.io.Reader
import java.util.Locale

/**
 * Data transfer model representing a single activity row parsed from Strava's `activities.csv`.
 *
 * Telemetry streams, timestamps, and lap records are parsed directly from `.fit.gz`, `.tcx.gz`,
 * and `.gpx.gz` stream files. This DTO encapsulates user-entered and cloud metadata required
 * for workout enrichment and duplicate re-upload suppression.
 *
 * Traceability:
 * - REQ-MIG-048.2: Language-Independent & Multi-Line RFC 4180 CSV Metadata Parser.
 * - TST-MIG-046.1: Language-Independent Strava CSV Parsing & Metric Pruning Contract.
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
    val media: List<String> = emptyList()
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
 * Streaming RFC 4180 compliant CSV parser supporting multi-language header aliases,
 * content pattern auto-detection, fixed canonical column fallbacks, escaped quotes,
 * and Strava metadata extraction (REQ-MIG-048.2).
 */
object StravaCsvParser {

    private val FILENAME_PATTERN = Regex("""^activities/[^\s]+""", RegexOption.IGNORE_CASE)
    private val MEDIA_PATTERN = Regex("""(?:^|\|)media/[^\s]+""", RegexOption.IGNORE_CASE)
    private val MEDIA_FILE_PATTERN = Regex("""(?:^|/)(?:media/)?[^/\s]+\.(?:jpg|jpeg|png|webp)""", RegexOption.IGNORE_CASE)

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
     * Parses `activities.csv` into a list of [StravaActivityRow], handling multi-language header names,
     * stream filename pattern auto-detection, and Strava's fixed canonical column indices (REQ-MIG-048.2).
     */
    fun parseActivities(reader: Reader): List<StravaActivityRow> {
        val records = parseCsv(reader)
        if (records.isEmpty()) return emptyList()

        val headers = records[0].map { normalize(it) }

        val idIdx = resolveIndex(
            findFirstHeader(headers, "aktivitäts-id", "activity id", "aktivitaets-id", "identifiant de l'activité", "identifiant de l'activite", "id de la actividad", "id dell'attività", "id dell'attivita", "id"),
            fallbackIdx = 0,
            headerCount = headers.size
        )
        val dateIdx = resolveIndex(
            findFirstHeader(headers, "aktivitätsdatum", "activity date", "aktivitaetsdatum", "date de l'activité", "date de l'activite", "fecha de la actividad", "data dell'attività", "data dell'attivita", "date"),
            fallbackIdx = 1,
            headerCount = headers.size
        )
        val nameIdx = resolveIndex(
            findFirstHeader(headers, "name der aktivität", "activity name", "name der aktivitaet", "nom de l'activité", "nom de l'activite", "nombre de la actividad", "titolo dell'attività", "titolo dell'attivita", "name", "title"),
            fallbackIdx = 2,
            headerCount = headers.size
        )
        val typeIdx = resolveIndex(
            findFirstHeader(headers, "aktivitätsart", "activity type", "aktivitaetsart", "type d'activité", "type d'activite", "tipo de actividad", "tipo di活性", "tipo di attività", "tipo di attivita", "sport", "type"),
            fallbackIdx = 3,
            headerCount = headers.size
        )
        val descIdx = resolveIndex(
            findFirstHeader(headers, "aktivitätsbeschreibung", "activity description", "aktivitaetsbeschreibung", "description de l'activité", "description de l'activite", "descripción de la actividad", "descripcion de la actividad", "descrizione dell'attività", "descrizione dell'attivita", "description"),
            fallbackIdx = 4,
            headerCount = headers.size
        )
        val commuteIdx = resolveIndex(
            findFirstHeader(headers, "pendeln", "commute", "trajet domicile-travail", "desplazamiento", "pendolare"),
            fallbackIdx = 9,
            headerCount = headers.size
        )
        val noteIdx = resolveIndex(
            findFirstHeader(headers, "hinweis zur privatsphäre für aktivitäten", "activity private note", "private note", "hinweis zur privatsphaere fuer aktivitaeten", "note privée", "note privee", "nota privada", "nota privata"),
            fallbackIdx = 10,
            headerCount = headers.size
        )
        val gearIdx = resolveIndex(
            findFirstHeader(headers, "aktivitätsausrüstung", "activity gear", "gear", "ausrüstung", "fahrrad", "aktivitaetsausruestung", "équipement de l'activité", "equipement de l'activite", "material de la actividad", "attrezzatura dell'attività", "attrezzatura dell'attivita"),
            fallbackIdx = 11,
            headerCount = headers.size
        )

        // Filename: Alias match -> Regex pattern auto-detection -> Fixed canonical index 12 fallback
        val rawFilenameIdx = findFirstHeader(headers, "dateiname", "filename", "file name", "nom du fichier", "nombre de archivo", "nome del file")
        val detectedFilenameIdx = if (rawFilenameIdx != -1) -1 else detectColumnPattern(records, 1, FILENAME_PATTERN)
        val filenameIdx = when {
            rawFilenameIdx != -1 -> rawFilenameIdx
            detectedFilenameIdx != -1 -> detectedFilenameIdx
            12 < headers.size -> 12
            else -> -1
        }

        // Media: Alias match -> Regex pattern auto-detection -> Fixed canonical index 102 fallback
        val rawMediaIdx = findFirstHeader(headers, "medien", "media", "médias", "medias")
        val detectedMediaIdx = if (rawMediaIdx != -1) -1 else detectColumnPattern(records, 1, MEDIA_PATTERN)
        val mediaIdx = when {
            rawMediaIdx != -1 -> rawMediaIdx
            detectedMediaIdx != -1 -> detectedMediaIdx
            102 < headers.size -> 102
            else -> -1
        }

        val result = mutableListOf<StravaActivityRow>()
        for (i in 1 until records.size) {
            val row = records[i]
            val rawId = getField(row, idIdx)
            if (rawId.isNullOrBlank()) continue

            val rawType = getField(row, typeIdx)
            val sportType = mapSportType(rawType)

            val rawMedia = getField(row, mediaIdx)
            val mediaList = if (!rawMedia.isNullOrBlank()) {
                rawMedia.split('|')
                    .map { it.trim().replace('\\', '/') }
                    .filter { it.isNotBlank() }
            } else {
                emptyList()
            }

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
                    media = mediaList
                )
            )
        }
        return result
    }

    /**
     * Parses `bikes.csv` into a list of [StravaGearItem] supporting header aliases and canonical fixed indices (0: name, 1: brand, 2: model).
     */
    fun parseBikes(reader: Reader): List<StravaGearItem> {
        val records = parseCsv(reader)
        if (records.isEmpty()) return emptyList()

        val headers = records[0].map { normalize(it) }
        val nameIdx = resolveIndex(
            findFirstHeader(headers, "fahrradname", "bike name", "name", "nom du vélo", "nom du velo", "nombre de la bicicleta", "nome bicicletta"),
            fallbackIdx = 0,
            headerCount = headers.size
        )
        val brandIdx = resolveIndex(
            findFirstHeader(headers, "fahrradmarke", "bike brand", "brand", "marque", "marca"),
            fallbackIdx = 1,
            headerCount = headers.size
        )
        val modelIdx = resolveIndex(
            findFirstHeader(headers, "fahrradmodell", "bike model", "model", "modèle", "modele", "modelo"),
            fallbackIdx = 2,
            headerCount = headers.size
        )

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
     * Parses `shoes.csv` into a list of [StravaGearItem] supporting header aliases and canonical fixed indices (0: name, 1: brand, 2: model).
     */
    fun parseShoes(reader: Reader): List<StravaGearItem> {
        val records = parseCsv(reader)
        if (records.isEmpty()) return emptyList()

        val headers = records[0].map { normalize(it) }
        val nameIdx = resolveIndex(
            findFirstHeader(headers, "schuhname", "shoe name", "name", "nom de la chaussure", "nombre de la zapatilla", "nome scarpa"),
            fallbackIdx = 0,
            headerCount = headers.size
        )
        val brandIdx = resolveIndex(
            findFirstHeader(headers, "schuhmarke", "shoe brand", "brand", "marque", "marca"),
            fallbackIdx = 1,
            headerCount = headers.size
        )
        val modelIdx = resolveIndex(
            findFirstHeader(headers, "schuhmodell", "shoe model", "model", "modèle", "modele", "modelo"),
            fallbackIdx = 2,
            headerCount = headers.size
        )

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
     * Parses `media.csv` into a map of media filenames to captions (REQ-MIG-048.6 / ATT-43),
     * supporting aliases, content pattern detection, and fixed canonical column fallbacks.
     */
    fun parseMediaCaptions(reader: Reader): Map<String, String> {
        val records = parseCsv(reader)
        if (records.isEmpty()) return emptyMap()

        val headers = records[0].map { normalize(it) }
        val rawFilenameIdx = findFirstHeader(headers, "medien-dateiname", "media filename", "filename", "file name", "nom du fichier", "nombre de archivo", "nome del file")
        val detectedFilenameIdx = if (rawFilenameIdx != -1) -1 else detectColumnPattern(records, 1, MEDIA_FILE_PATTERN)
        val filenameIdx = when {
            rawFilenameIdx != -1 -> rawFilenameIdx
            detectedFilenameIdx != -1 -> detectedFilenameIdx
            headers.isNotEmpty() -> 0
            else -> -1
        }
        val captionIdx = resolveIndex(
            findFirstHeader(headers, "medienunterschrift", "media caption", "caption", "légende", "legende", "pie de foto", "didascalia"),
            fallbackIdx = 1,
            headerCount = headers.size
        )

        val captions = mutableMapOf<String, String>()
        for (i in 1 until records.size) {
            val row = records[i]
            val file = getField(row, filenameIdx)?.trim()?.replace('\\', '/')
            val caption = getField(row, captionIdx)?.trim()
            if (!file.isNullOrBlank() && !caption.isNullOrBlank()) {
                captions[file] = caption
                captions[File(file).name] = caption
            }
        }
        return captions
    }

    /**
     * Maps localized Strava activity type strings to canonical [BSportType].
     */
    fun mapSportType(type: String?): BSportType {
        if (type.isNullOrBlank()) return BSportType.UNKNOWN
        val lower = type.trim().lowercase(Locale.ROOT)
        return when {
            lower.contains("ride") || lower.contains("radfahrt") || lower.contains("cycling") ||
                    lower.contains("bike") || lower.contains("velomobile") || lower.contains("vélo") ||
                    lower.contains("velo") || lower.contains("bicicleta") || lower.contains("ciclismo") ||
                    lower.contains("pedalata") || lower.contains("fiets") || lower.contains("rower") ||
                    lower.contains("kolarstwo") || lower.contains("ライド") || lower.contains("サイクリング") -> BSportType.BIKE

            lower.contains("run") || lower.contains("lauf") || lower.contains("walk") ||
                    lower.contains("spaziergang") || lower.contains("hike") || lower.contains("wander") ||
                    lower.contains("course") || lower.contains("marche") || lower.contains("randonn") ||
                    lower.contains("carrera") || lower.contains("caminar") || lower.contains("paseo") ||
                    lower.contains("senderismo") || lower.contains("corsa") || lower.contains("camminata") ||
                    lower.contains("escursione") || lower.contains("hardlopen") || lower.contains("bieg") ||
                    lower.contains("spacer") || lower.contains("corrida") || lower.contains("caminhada") ||
                    lower.contains("ラン") || lower.contains("ウォーキング") || lower.contains("ハイキング") -> BSportType.RUN

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

    private fun resolveIndex(matchedIdx: Int, fallbackIdx: Int, headerCount: Int): Int {
        return if (matchedIdx != -1) matchedIdx else if (fallbackIdx < headerCount) fallbackIdx else -1
    }

    private fun detectColumnPattern(records: List<List<String>>, startRow: Int, pattern: Regex, maxRowsToScan: Int = 50): Int {
        val limit = minOf(records.size, startRow + maxRowsToScan)
        for (i in startRow until limit) {
            val row = records[i]
            for (col in row.indices) {
                val value = row[col].trim()
                if (value.isNotEmpty() && pattern.containsMatchIn(value)) {
                    return col
                }
            }
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
        return clean == "true" || clean == "1" || clean == "ja" || clean == "yes" ||
                clean == "oui" || clean == "si" || clean == "sí" || clean == "sim" ||
                clean == "tak" || clean == "waar" || clean == "はい"
    }
}
