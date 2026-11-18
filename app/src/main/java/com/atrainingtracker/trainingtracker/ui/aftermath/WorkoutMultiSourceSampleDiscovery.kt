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

import android.database.Cursor
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricType
import com.atrainingtracker.trainingtracker.ui.map.TelemetrySourcePoint
import com.atrainingtracker.trainingtracker.ui.map.TelemetrySourceSeries
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import java.util.Locale

/**
 * Discovers multi-source track and telemetry columns inside `WorkoutSamples.db` tables and
 * accumulates pre-decimation mean-preserving overlay series (`REQ-MAP-011`, `REQ-PER-042.1`,
 * `REQ-PER-044.2`). Extracted from [WorkoutRepository] to respect LoC architecture bounds.
 */
object WorkoutMultiSourceSampleDiscovery {

    internal const val MAX_OVERLAY_POINTS = 800

    private val OVERLAY_SENSOR_MAPPINGS: List<Pair<SensorType, TelemetryMetricType>> = listOf(
        SensorType.ALTITUDE to TelemetryMetricType.ALTITUDE,
        SensorType.SPEED_mps to TelemetryMetricType.SPEED,
        SensorType.SLOPE to TelemetryMetricType.SLOPE,
        SensorType.HR to TelemetryMetricType.HEART_RATE,
        SensorType.POWER to TelemetryMetricType.POWER,
        SensorType.CADENCE to TelemetryMetricType.CADENCE
    )

    private val SOURCE_DISPLAY_ALIASES: Map<String, String> = mapOf(
        "gps" to "GPS",
        "google_fused" to "Google Fused",
        "att_fused" to "ATT Fused",
        "att_fused_altitude_slope" to "ATT Fused (Alt/Slope)"
    )

    /**
     * Resolves the `(latitudeColumn, longitudeColumn)` pair for [trackType] against [existingColumns],
     * supporting both exact technical names (`LATITUDE_gps`, `LATITUDE_att_fused`), legacy parenthesized
     * names (`"LATITUDE (GPS)"`), and localized device aliases (`"LATITUDE (Phone GPS (Satellite))"`,
     * `"LATITUDE (Smartphone GPS (Satellit))"`, `"LATITUDE (Google Location (Fused))"`).
     */
    fun resolveTrackColumns(
        existingColumns: Collection<String>,
        trackType: TrackType
    ): Pair<String, String>? {
        val columnSet = existingColumns as? Set<String> ?: existingColumns.toSet()
        if (trackType == TrackType.BEST) {
            val lat = SensorType.LATITUDE.name
            val lon = SensorType.LONGITUDE.name
            return if (lat in columnSet && lon in columnSet) lat to lon else null
        }

        val candidates: List<Pair<String, String>> = when (trackType) {
            TrackType.BEST -> emptyList()
            TrackType.GPS -> listOf(
                "${SensorType.LATITUDE.name} (GPS)" to "${SensorType.LONGITUDE.name} (GPS)",
                "${SensorType.LATITUDE.name}_gps" to "${SensorType.LONGITUDE.name}_gps"
            )
            TrackType.FUSED -> listOf(
                "${SensorType.LATITUDE.name} (Google Fused)" to "${SensorType.LONGITUDE.name} (Google Fused)",
                "${SensorType.LATITUDE.name}_google_fused" to "${SensorType.LONGITUDE.name}_google_fused"
            )
            TrackType.ATT_FUSED -> listOf(
                "${SensorType.LATITUDE.name}_att_fused" to "${SensorType.LONGITUDE.name}_att_fused",
                "${SensorType.LATITUDE.name} (att_fused)" to "${SensorType.LONGITUDE.name} (att_fused)",
                "${SensorType.LATITUDE.name} (ATT Fused)" to "${SensorType.LONGITUDE.name} (ATT Fused)"
            )
        }
        candidates.firstOrNull { (lat, lon) -> lat in columnSet && lon in columnSet }?.let { return it }

        val latParenPattern = Regex("^${Regex.escape(SensorType.LATITUDE.name)} \\((.+)\\)$")
        return columnSet
            .mapNotNull { colName ->
                val rawSource = latParenPattern.matchEntire(colName)?.groupValues?.getOrNull(1)?.trim()
                    ?: return@mapNotNull null
                val lonCol = "${SensorType.LONGITUDE.name} ($rawSource)"
                if (lonCol !in columnSet) return@mapNotNull null
                if (matchesTrackAlias(trackType, rawSource)) colName to lonCol else null
            }
            .sortedBy { it.first.lowercase(Locale.US) }
            .firstOrNull()
    }

    private fun matchesTrackAlias(trackType: TrackType, rawSource: String): Boolean {
        val lower = rawSource.lowercase(Locale.US)
        return when (trackType) {
            TrackType.BEST -> false
            TrackType.GPS -> {
                (lower.contains("gps") || lower.contains("satellit")) &&
                    !lower.contains("fused") &&
                    !lower.contains("att")
            }
            TrackType.FUSED -> {
                lower.contains("google") &&
                    lower.contains("fused") &&
                    !lower.contains("att")
            }
            TrackType.ATT_FUSED -> {
                lower.contains("att_fused") ||
                    (lower.contains("att") &&
                        (lower.contains("fused") || lower.contains("odometrie") || lower.contains("odometry")))
            }
        }
    }

    /**
     * Discovers all source-qualified columns for [sensorType], supporting both
     * `"<SENSOR_TYPE> (<deviceName>)"` (including nested parentheses) and technical suffixes
     * `"<SENSOR_TYPE>_(gps|google_fused|att_fused)"` (`TrackerService.java:2740`, excluding `_network`).
     */
    fun discoverNamedSourceColumns(
        existingColumns: Collection<String>,
        sensorType: SensorType
    ): List<Pair<String, String>> {
        val escaped = Regex.escape(sensorType.name)
        val parenPattern = Regex("^$escaped \\((.+)\\)$")
        val suffixPattern = Regex("^${escaped}_(gps|google_fused|att_fused)$")

        val seenColumns = HashSet<String>()
        val discovered = mutableListOf<Pair<String, String>>()

        for (colName in existingColumns) {
            if (!seenColumns.add(colName)) continue
            val rawSource = parenPattern.matchEntire(colName)?.groupValues?.getOrNull(1)
                ?: suffixPattern.matchEntire(colName)?.groupValues?.getOrNull(1)
                ?: continue
            val displayName = normalizeSourceDisplayName(rawSource)
            if (displayName.isNotEmpty()) {
                discovered.add(displayName to colName)
            }
        }
        return discovered.sortedWith(
            compareBy<Pair<String, String>> { it.first.lowercase(Locale.US) }.thenBy { it.second }
        )
    }

    /**
     * Maps overlay-eligible metrics (`ALTITUDE`, `SPEED_mps`, `SLOPE`, `HR`, `POWER`, `CADENCE`)
     * that have at least 2 distinct source columns (`REQ-PER-042.1`, `REQ-PER-044.2`).
     */
    fun discoverMultiSourceOverlayColumns(
        existingColumns: Collection<String>
    ): Map<TelemetryMetricType, List<Pair<String, String>>> {
        val result = linkedMapOf<TelemetryMetricType, List<Pair<String, String>>>()
        for ((sensorType, metricType) in OVERLAY_SENSOR_MAPPINGS) {
            val sources = discoverNamedSourceColumns(existingColumns, sensorType)
            if (sources.size >= 2) {
                result[metricType] = sources
            }
        }
        return result
    }

    /**
     * Normalizes internal technical sensor suffixes/names (e.g. `att_fused`,
     * `att_fused_altitude_slope`) into clean display identifiers (`REQ-PER-044.2`).
     */
    fun normalizeSourceDisplayName(rawName: String): String {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return ""
        return SOURCE_DISPLAY_ALIASES[trimmed.lowercase(Locale.US)] ?: trimmed
    }

    /**
     * Validates a scalar sample according to [metricType] semantics (`REQ-PER-044.2`):
     * `ALTITUDE` and `SLOPE` permit negative/zero readings (`isFinite()`); `SPEED` and `POWER`
     * permit `0.0`; `HR` and `CADENCE` retain strict `> 0.0` dropout filtering.
     */
    fun isValidOverlaySample(metricType: TelemetryMetricType, raw: Double): Boolean {
        if (!raw.isFinite()) return false
        return when (metricType) {
            TelemetryMetricType.ALTITUDE,
            TelemetryMetricType.SLOPE -> true
            TelemetryMetricType.SPEED,
            TelemetryMetricType.POWER -> raw >= 0.0
            else -> raw > 0.0
        }
    }

    internal fun decimateSourcePoints(
        points: List<TelemetrySourcePoint>,
        maxPoints: Int = MAX_OVERLAY_POINTS
    ): List<TelemetrySourcePoint> {
        if (points.size <= maxPoints) return points
        val step = kotlin.math.ceil(points.size.toDouble() / maxPoints).toInt()
        return points.filterIndexed { index, _ ->
            index == 0 || index == points.lastIndex || index % step == 0
        }
    }

    internal class OverlaySeriesAccumulator(
        val metricType: TelemetryMetricType,
        val sourceName: String,
        val columnName: String,
        val columnIndex: Int
    ) {
        private val points = mutableListOf<TelemetrySourcePoint>()
        private var sum = 0.0
        private var count = 0

        fun record(cursor: Cursor, distance: Double, timeSec: Long) {
            if (columnIndex < 0 || cursor.isNull(columnIndex)) return
            val raw = cursor.getDouble(columnIndex)
            if (isValidOverlaySample(metricType, raw)) {
                sum += raw
                count++
                points.add(TelemetrySourcePoint(distance = distance, timeSec = timeSec, value = raw))
            }
        }

        fun build(): TelemetrySourceSeries? {
            if (count == 0) return null
            return TelemetrySourceSeries(
                metricType = metricType,
                sourceName = sourceName,
                columnName = columnName,
                points = decimateSourcePoints(points),
                meanValue = sum / count
            )
        }
    }

    internal fun buildColumnIndexMap(cursor: Cursor): Map<String, Int> {
        val map = HashMap<String, Int>()
        val names = cursor.columnNames ?: return map
        names.forEachIndexed { idx, name -> map[name] = idx }
        return map
    }

    internal fun createOverlayAccumulators(
        overlayColumnsByMetric: Map<TelemetryMetricType, List<Pair<String, String>>>,
        columnIndexMap: Map<String, Int>
    ): List<OverlaySeriesAccumulator> {
        val list = mutableListOf<OverlaySeriesAccumulator>()
        for ((metricType, sources) in overlayColumnsByMetric) {
            for ((sourceName, colName) in sources) {
                val idx = columnIndexMap[colName] ?: -1
                if (idx >= 0) {
                    list.add(OverlaySeriesAccumulator(metricType, sourceName, colName, idx))
                }
            }
        }
        return list
    }

    internal fun finalizeOverlaySeries(
        accumulators: List<OverlaySeriesAccumulator>
    ): Map<TelemetryMetricType, List<TelemetrySourceSeries>> {
        val grouped = accumulators.mapNotNull { it.build() }.groupBy { it.metricType }
        return grouped.filterValues { it.size >= 2 }
    }
}
