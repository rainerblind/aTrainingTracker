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
    val segmentEfforts: List<StravaSegmentEffort> = emptyList(),
    val bestEfforts: List<StravaBestEffort> = emptyList()
)

data class StravaSegmentEffort(
    val name: String,
    val elapsedTimeSec: Int,
    val prRank: Int?, // 1, 2, 3
    val komRank: Int? // 1
)

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
            
            val segmentEfforts = mutableListOf<StravaSegmentEffort>()
            json.optJSONArray("segment_efforts")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    segmentEfforts.add(
                        StravaSegmentEffort(
                            name = item.optString("name"),
                            elapsedTimeSec = item.optInt("elapsed_time"),
                            prRank = if (item.has("pr_rank") && !item.isNull("pr_rank")) item.optInt("pr_rank") else null,
                            komRank = if (item.has("kom_rank") && !item.isNull("kom_rank")) item.optInt("kom_rank") else null
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

            StravaActivity(segmentEfforts, bestEfforts)
        } catch (e: Exception) {
            null
        }
    }
}
