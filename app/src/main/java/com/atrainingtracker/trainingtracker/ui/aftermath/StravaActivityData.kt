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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class StravaActivity(
    val id: Long? = null,
    val segmentEfforts: List<StravaSegmentEffort> = emptyList(),
    val bestEfforts: List<StravaBestEffort> = emptyList()
)

data class StravaSegmentEffort(
    val name: String,
    val elapsedTimeSec: Int,
    val prRank: Int?, // 1, 2, 3
    val komRank: Int?, // 1
    val isStarred: Boolean = false,
    val segmentId: Long? = null
)

val StravaSegmentEffort.isHighlight: Boolean
    get() = isStarred || prRank != null || komRank != null

data class StravaBestEffort(
    val name: String,
    val elapsedTimeSec: Int,
    val prRank: Int? = null,
    val distanceMeters: Double = 0.0
)

val StravaBestEffort.isHighlight: Boolean
    get() = prRank != null && prRank in 1..3

val StravaBestEffort.isMileEffort: Boolean
    get() = name.contains("mile", ignoreCase = true) || name.contains(" mi", ignoreCase = true)

val StravaBestEffort.effectiveDistanceMeters: Double
    get() {
        if (distanceMeters > 0.0) return distanceMeters
        val lower = name.trim().lowercase()
        return when {
            lower == "400m" -> 400.0
            lower.contains("1/2") && lower.contains("mile") -> 804.672
            lower == "1k" -> 1000.0
            lower == "1 mile" || lower == "1 mi" -> 1609.344
            lower == "2 miles" || lower == "2 mi" -> 3218.688
            lower == "5k" -> 5000.0
            lower == "10k" -> 10000.0
            lower == "15k" -> 15000.0
            lower == "10 miles" || lower == "10 mi" -> 16093.44
            lower == "20k" -> 20000.0
            lower.contains("half") && lower.contains("marathon") -> 21097.5
            lower.contains("marathon") -> 42195.0
            else -> 0.0
        }
    }

/**
 * Serializes the domain [StravaActivity] to a minimized JSON payload adhering to schema version 2 (REQ-EXP-013).
 * Transient social and third-party fields are completely omitted. Optional fields that are null,
 * non-positive, or false are omitted rather than serialized as explicit nulls.
 */
fun StravaActivity.toJson(version: Int = 2): String {
    val root = JSONObject()
    root.put("v", version)
    if (id != null && id > 0) {
        root.put("id", id)
    }

    if (segmentEfforts.isNotEmpty()) {
        val segArray = JSONArray()
        for (effort in segmentEfforts) {
            val effortObj = JSONObject()
            effortObj.put("name", effort.name)
            effortObj.put("elapsed_time", effort.elapsedTimeSec)
            effort.prRank?.let { effortObj.put("pr_rank", it) }
            effort.komRank?.let { effortObj.put("kom_rank", it) }
            if (effort.isStarred) {
                effortObj.put("starred", true)
            }
            effort.segmentId?.let { if (it > 0) effortObj.put("segment_id", it) }
            segArray.put(effortObj)
        }
        root.put("segment_efforts", segArray)
    }

    if (bestEfforts.isNotEmpty()) {
        val bestArray = JSONArray()
        for (effort in bestEfforts) {
            val effortObj = JSONObject()
            effortObj.put("name", effort.name)
            effortObj.put("elapsed_time", effort.elapsedTimeSec)
            effort.prRank?.let { effortObj.put("pr_rank", it) }
            if (effort.distanceMeters > 0.0) {
                effortObj.put("distance", effort.distanceMeters)
            }
            bestArray.put(effortObj)
        }
        root.put("best_efforts", bestArray)
    }

    return root.toString()
}

object StravaActivityParser {
    private const val TAG = "StravaActivityParser"

    /**
     * Parses either a legacy full Strava API response or a minimized version 2 payload
     * into a domain [StravaActivity] (REQ-EXP-013).
     *
     * Returns null safely on empty, blank, malformed, or truncated JSON without throwing exceptions.
     */
    @JvmStatic
    fun parse(jsonString: String?): StravaActivity? {
        if (jsonString.isNullOrBlank()) return null
        return try {
            val json = JSONObject(jsonString)
            val id = if (json.has("id") && !json.isNull("id")) json.getLong("id") else null
            
            val segmentEfforts = mutableListOf<StravaSegmentEffort>()
            json.optJSONArray("segment_efforts")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val segmentObj = item.optJSONObject("segment")
                    val isStarred = item.optBoolean("starred", false) ||
                            item.optBoolean("is_starred", false) ||
                            (item.has("starred_date") && !item.isNull("starred_date") && item.optString("starred_date").isNotBlank()) ||
                            (segmentObj != null && (
                                segmentObj.optBoolean("starred", false) ||
                                segmentObj.optBoolean("is_starred", false) ||
                                (segmentObj.has("starred_date") && !segmentObj.isNull("starred_date") && segmentObj.optString("starred_date").isNotBlank())
                            ))
                    val segmentId = if (segmentObj != null && segmentObj.has("id") && !segmentObj.isNull("id")) {
                        segmentObj.optLong("id")
                    } else if (item.has("segment_id") && !item.isNull("segment_id")) {
                        item.optLong("segment_id")
                    } else {
                        null
                    }

                    var prRank = if (item.has("pr_rank") && !item.isNull("pr_rank")) item.optInt("pr_rank") else null
                    var komRank = if (item.has("kom_rank") && !item.isNull("kom_rank")) item.optInt("kom_rank") else null

                    // Check achievements array if pr_rank or kom_rank is omitted (legacy v1 payloads)
                    item.optJSONArray("achievements")?.let { achievementsArray ->
                        for (a in 0 until achievementsArray.length()) {
                            val ach = achievementsArray.optJSONObject(a) ?: continue
                            val type = ach.optString("type")
                            val typeId = ach.optInt("type_id", -1)
                            val rank = if (ach.has("rank") && !ach.isNull("rank")) ach.optInt("rank") else 1
                            if (prRank == null && (type.equals("pr", ignoreCase = true) || typeId == 2 || typeId == 3)) {
                                prRank = rank
                            }
                            if (komRank == null && (type.equals("overall", ignoreCase = true) || type.equals("kom", ignoreCase = true))) {
                                komRank = rank
                            }
                        }
                    }

                    segmentEfforts.add(
                        StravaSegmentEffort(
                            name = item.optString("name"),
                            elapsedTimeSec = item.optInt("elapsed_time"),
                            prRank = prRank,
                            komRank = komRank,
                            isStarred = isStarred,
                            segmentId = segmentId
                        )
                    )
                }
            }

            val bestEfforts = mutableListOf<StravaBestEffort>()
            json.optJSONArray("best_efforts")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    var prRank = if (item.has("pr_rank") && !item.isNull("pr_rank")) item.optInt("pr_rank") else null
                    if (prRank == null) {
                        item.optJSONArray("achievements")?.let { achArray ->
                            for (a in 0 until achArray.length()) {
                                val ach = achArray.optJSONObject(a) ?: continue
                                val type = ach.optString("type")
                                val typeId = ach.optInt("type_id", -1)
                                val rank = if (ach.has("rank") && !ach.isNull("rank")) ach.optInt("rank") else 1
                                if (type.equals("pr", ignoreCase = true) || typeId == 2 || typeId == 3) {
                                    prRank = rank
                                    break
                                }
                            }
                        }
                    }
                    val distanceMeters = if (item.has("distance") && !item.isNull("distance")) item.optDouble("distance", 0.0) else 0.0
                    bestEfforts.add(
                        StravaBestEffort(
                            name = item.optString("name"),
                            elapsedTimeSec = item.optInt("elapsed_time"),
                            prRank = prRank,
                            distanceMeters = distanceMeters
                        )
                    )
                }
            }

            StravaActivity(id, segmentEfforts, bestEfforts)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Strava activity JSON, treating as null", e)
            null
        }
    }

    /**
     * Minimizes a raw or parsed Strava activity JSON string to the athlete-achievement schema (REQ-EXP-013).
     * Discards all transient third-party social data, photos, maps, kudos, comments, and profile information.
     */
    @JvmStatic
    fun minimize(rawActivityJson: String?): String? {
        if (rawActivityJson.isNullOrBlank()) return null
        val activity = parse(rawActivityJson) ?: return null
        return activity.toJson(version = 2)
    }

    /**
     * Minimizes a [JSONObject] representation of a Strava activity response to the schema v2 string.
     */
    @JvmStatic
    fun minimize(activityJson: JSONObject?): String? {
        if (activityJson == null) return null
        return minimize(activityJson.toString())
    }
}
