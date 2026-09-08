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
    val prRank: Int?
)

object StravaActivityParser {
    fun parse(jsonString: String?): StravaActivity? {
        if (jsonString.isNullOrBlank()) return null
        return try {
            val json = JSONObject(jsonString)
            val id = if (json.has("id")) json.getLong("id") else null
            
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

                    // Check achievements array if pr_rank or kom_rank is omitted
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
                    bestEfforts.add(
                        StravaBestEffort(
                            name = item.optString("name"),
                            elapsedTimeSec = item.optInt("elapsed_time"),
                            prRank = if (item.has("pr_rank") && !item.isNull("pr_rank")) item.optInt("pr_rank") else null
                        )
                    )
                }
            }

            StravaActivity(id, segmentEfforts, bestEfforts)
        } catch (e: Exception) {
            null
        }
    }
}
