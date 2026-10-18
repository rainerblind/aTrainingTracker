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

import androidx.annotation.StringRes
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Enumeration of all 10 assignable sections in the multi-tab Workout Details screen (`REQ-UI-408.1` / `ATT-3920`).
 *
 * Note: [WorkoutSectionType] (9 values) remains unchanged for the Workout Summary list card (`WorkoutSummary.kt`),
 * where `WorkoutDetails` is permanently anchored above the dynamic section loop.
 */
enum class WorkoutDetailSectionType(@StringRes val labelRes: Int) {
    DETAILS(R.string.workout_detail_section_details),
    DESCRIPTION(R.string.settings_workout_card_description),
    EXTREMA(R.string.settings_workout_card_extrema),
    MAP(R.string.settings_workout_card_map),
    ELEVATION(R.string.settings_workout_card_elevation),
    CHARTS(R.string.settings_workout_card_charts),
    ZONES(R.string.settings_workout_card_zones),
    LAPS(R.string.settings_workout_card_laps),
    STRAVA(R.string.settings_workout_card_strava),
    EXPORT_STATUS(R.string.export_status);

    /**
     * Maps this [WorkoutDetailSectionType] to its corresponding 9-item [WorkoutSectionType],
     * or returns `null` for [DETAILS] (which is permanently anchored at the top of `WorkoutSummary`).
     */
    fun toWorkoutSectionTypeOrNull(): WorkoutSectionType? = when (this) {
        DETAILS -> null
        DESCRIPTION -> WorkoutSectionType.DESCRIPTION
        EXTREMA -> WorkoutSectionType.EXTREMA
        MAP -> WorkoutSectionType.MAP
        ELEVATION -> WorkoutSectionType.ELEVATION
        CHARTS -> WorkoutSectionType.CHARTS
        ZONES -> WorkoutSectionType.ZONES
        LAPS -> WorkoutSectionType.LAPS
        STRAVA -> WorkoutSectionType.STRAVA
        EXPORT_STATUS -> WorkoutSectionType.EXPORT_STATUS
    }
}

/**
 * Maps a 9-item [WorkoutSectionType] to its corresponding 10-item [WorkoutDetailSectionType].
 */
fun WorkoutSectionType.toWorkoutDetailSectionType(): WorkoutDetailSectionType = when (this) {
    WorkoutSectionType.DESCRIPTION -> WorkoutDetailSectionType.DESCRIPTION
    WorkoutSectionType.EXTREMA -> WorkoutDetailSectionType.EXTREMA
    WorkoutSectionType.LAPS -> WorkoutDetailSectionType.LAPS
    WorkoutSectionType.STRAVA -> WorkoutDetailSectionType.STRAVA
    WorkoutSectionType.MAP -> WorkoutDetailSectionType.MAP
    WorkoutSectionType.ELEVATION -> WorkoutDetailSectionType.ELEVATION
    WorkoutSectionType.CHARTS -> WorkoutDetailSectionType.CHARTS
    WorkoutSectionType.ZONES -> WorkoutDetailSectionType.ZONES
    WorkoutSectionType.EXPORT_STATUS -> WorkoutDetailSectionType.EXPORT_STATUS
}

/**
 * Derives a complete 9-element [WorkoutSectionType] summary order from a list of [WorkoutDetailTabConfig] tabs,
 * appending any unassigned sections from [fallbackOrder] so zero summary sections are lost (`REQ-UI-408.2`).
 */
fun deriveSummarySectionOrder(
    tabs: List<WorkoutDetailTabConfig>,
    fallbackOrder: List<WorkoutSectionType> = WorkoutSectionType.DEFAULT_ORDER
): List<WorkoutSectionType> {
    val fromTabs = tabs
        .flatMap { it.normalizedSections }
        .mapNotNull { it.toWorkoutSectionTypeOrNull() }
        .distinct()
    val missing = fallbackOrder.filter { it !in fromTabs } +
        WorkoutSectionType.entries.filter { it !in fromTabs && it !in fallbackOrder }
    return (fromTabs + missing).distinct()
}

/**
 * Preset tab archetypes providing localized default titles (`REQ-UI-408.1`).
 */
enum class WorkoutDetailTabPreset(@StringRes val defaultTitleRes: Int) {
    OVERVIEW(R.string.workout_detail_tab_overview),
    MAP_AND_GRAPHS(R.string.workout_detail_tab_map_graphs),
    ZONES(R.string.workout_detail_tab_zones),
    LAPS_AND_SEGMENTS(R.string.workout_detail_tab_laps_segments),
    CUSTOM(R.string.workout_detail_tab_custom_default)
}

/**
 * Conflict-free per-tab layout modes (`REQ-UI-408.3`):
 * - [MODE_A_CARD_SCROLL]: No `MAP` in tab -> single unified vertical scroll column (with sticky zoom toolbar if `ELEVATION` or `CHARTS` are present).
 * - [MODE_B_SPLIT_MAP]: `MAP` + at least one other section -> `MAP` pinned at top (`index 0`), `SplitPaneDivider`, and a single lower scrollable pane.
 * - [MODE_C_FULL_MAP]: Only `MAP` in tab -> 100% height map without `SplitPaneDivider` or lower scroll pane.
 */
enum class WorkoutDetailTabLayoutMode {
    MODE_A_CARD_SCROLL,
    MODE_B_SPLIT_MAP,
    MODE_C_FULL_MAP
}

/**
 * Per-workout data availability flags used to auto-hide empty tabs and evaluate gesture isolation (`REQ-UI-408.4`).
 */
data class WorkoutDetailSectionAvailability(
    val hasDetails: Boolean = true,
    val hasDescription: Boolean = false,
    val hasExtrema: Boolean = false,
    val hasMap: Boolean = false,
    val hasElevation: Boolean = false,
    val hasCharts: Boolean = false,
    val hasZones: Boolean = false,
    val hasLaps: Boolean = false,
    val hasStrava: Boolean = false,
    val hasExportStatus: Boolean = false
) {
    /**
     * Returns whether [section] is both enabled in [detailPreferences] and has non-empty data for the current workout.
     */
    fun isSectionActive(
        section: WorkoutDetailSectionType,
        detailPreferences: WorkoutDetailPreferences = WorkoutDetailPreferences()
    ): Boolean = when (section) {
        WorkoutDetailSectionType.DETAILS -> hasDetails
        WorkoutDetailSectionType.DESCRIPTION -> detailPreferences.showDescription && hasDescription
        WorkoutDetailSectionType.EXTREMA -> detailPreferences.showExtrema && hasExtrema
        WorkoutDetailSectionType.MAP -> detailPreferences.showMap && hasMap
        WorkoutDetailSectionType.ELEVATION -> detailPreferences.showElevationProfile && hasElevation
        WorkoutDetailSectionType.CHARTS -> detailPreferences.showTelemetryCharts && hasCharts
        WorkoutDetailSectionType.ZONES -> detailPreferences.showZoneAnalysis && hasZones
        WorkoutDetailSectionType.LAPS -> detailPreferences.showLaps && hasLaps
        WorkoutDetailSectionType.STRAVA -> detailPreferences.showStrava && hasStrava
        WorkoutDetailSectionType.EXPORT_STATUS -> detailPreferences.showExportStatus && hasExportStatus
    }

    companion object {
        /**
         * Computes [WorkoutDetailSectionAvailability] from workout domain data and computed telemetry flags (`REQ-UI-408.4`).
         */
        fun fromWorkoutState(
            workoutData: WorkoutData,
            showMap: Boolean,
            hasGpsTrack: Boolean,
            hasTracklessAltitude: Boolean,
            hasAltitudeData: Boolean,
            hasTelemetrySeries: Boolean,
            hasZonesData: Boolean,
            hasLapsData: Boolean
        ): WorkoutDetailSectionAvailability {
            val desc = workoutData.descriptionData
            val hasDescriptionContent = !desc.description.isNullOrBlank() ||
                !desc.goal.isNullOrBlank() ||
                !desc.method.isNullOrBlank()
            return WorkoutDetailSectionAvailability(
                hasDetails = true,
                hasDescription = hasDescriptionContent,
                hasExtrema = workoutData.extremaData.dataRows.isNotEmpty(),
                hasMap = showMap && hasGpsTrack,
                hasElevation = (hasGpsTrack || hasTracklessAltitude) && hasAltitudeData,
                hasCharts = hasTelemetrySeries,
                hasZones = hasZonesData,
                hasLaps = hasLapsData,
                hasStrava = !workoutData.stravaActivityData.isNullOrBlank(),
                hasExportStatus = workoutData.exportStatuses.any { it.hasContent }
            )
        }
    }
}

/**
 * Immutable configuration for a single tab in the Workout Details view (`REQ-UI-408`).
 */
data class WorkoutDetailTabConfig(
    val id: String,
    val preset: WorkoutDetailTabPreset,
    val customTitle: String = "",
    val sections: List<WorkoutDetailSectionType>
) {
    /**
     * Deduplicated sections list with [WorkoutDetailSectionType.MAP] strictly pinned at index `0` whenever present (`REQ-UI-408.3`).
     */
    val normalizedSections: List<WorkoutDetailSectionType>
        get() = normalizeTabSections(sections)

    /**
     * Explicitly validates and normalizes this tab configuration, enforcing the "Map-at-top" (`index 0`) invariant
     * and deduplicating assigned sections (`REQ-UI-408.3` / `REQ-UI-408.5`).
     */
    fun validateConfig(): WorkoutDetailTabConfig = copy(sections = normalizeTabSections(sections))

    /**
     * Resolves the conflict-free layout mode for this tab based on its [normalizedSections] (`REQ-UI-408.3`).
     */
    val layoutMode: WorkoutDetailTabLayoutMode
        get() = resolveTabLayoutMode(normalizedSections)

    /**
     * Evaluates the effective layout mode for the current workout given [availability] and [detailPreferences].
     */
    fun effectiveLayoutMode(
        availability: WorkoutDetailSectionAvailability,
        detailPreferences: WorkoutDetailPreferences = WorkoutDetailPreferences()
    ): WorkoutDetailTabLayoutMode {
        val activeSections = normalizedSections.filter { availability.isSectionActive(it, detailPreferences) }
        return resolveTabLayoutMode(activeSections.ifEmpty { normalizedSections })
    }

    /**
     * Evaluates whether horizontal pager swipe should be isolated (`userScrollEnabled = false`)
     * to protect 2D map panning and horizontal telemetry chart scrubbing (`REQ-UI-408.4`).
     *
     * Supports hit-test & edge-gutter arbitration:
     * - Returns `false` when the tab has no active `MAP`, `ELEVATION`, or `CHARTS` for the workout.
     * - Returns `false` when the touch starts within [edgeSwipeGutterDp] of the left or right screen edge.
     * - Returns `false` when [isTouchingInteractiveCanvas] is `false` (e.g., user is touching non-chart cards in the lower pane of a `MODE_B_SPLIT_MAP` tab).
     * - Returns `true` when actively touching the Map viewport or Elevation/Telemetry chart canvases.
     */
    fun requiresHorizontalGestureIsolation(
        availability: WorkoutDetailSectionAvailability,
        detailPreferences: WorkoutDetailPreferences = WorkoutDetailPreferences(),
        isTouchingInteractiveCanvas: Boolean = true,
        touchXDp: Float? = null,
        screenWidthDp: Float? = null,
        edgeSwipeGutterDp: Float = 24f
    ): Boolean {
        if (touchXDp != null && screenWidthDp != null && screenWidthDp > edgeSwipeGutterDp * 2f) {
            if (touchXDp <= edgeSwipeGutterDp || touchXDp >= screenWidthDp - edgeSwipeGutterDp) {
                return false
            }
        }
        if (!isTouchingInteractiveCanvas) {
            return false
        }
        val activeSections = normalizedSections.filter { availability.isSectionActive(it, detailPreferences) }
        return activeSections.any {
            it == WorkoutDetailSectionType.MAP ||
                it == WorkoutDetailSectionType.ELEVATION ||
                it == WorkoutDetailSectionType.CHARTS
        }
    }

    companion object {
        private const val SCHEMA_PREFIX = "v1|"

        /**
         * Canonical 4-tab default configuration (`REQ-UI-408.1`):
         * 1. Overview: `[DETAILS, DESCRIPTION, EXTREMA, EXPORT_STATUS]`
         * 2. Map & Graphs: `[MAP, ELEVATION, CHARTS]`
         * 3. Zones: `[ZONES]`
         * 4. Laps & Segments: `[LAPS, STRAVA]`
         */
        val DEFAULT_TABS: List<WorkoutDetailTabConfig> = listOf(
            WorkoutDetailTabConfig(
                id = "tab_overview",
                preset = WorkoutDetailTabPreset.OVERVIEW,
                customTitle = "",
                sections = listOf(
                    WorkoutDetailSectionType.DETAILS,
                    WorkoutDetailSectionType.DESCRIPTION,
                    WorkoutDetailSectionType.EXTREMA,
                    WorkoutDetailSectionType.EXPORT_STATUS
                )
            ),
            WorkoutDetailTabConfig(
                id = "tab_map_graphs",
                preset = WorkoutDetailTabPreset.MAP_AND_GRAPHS,
                customTitle = "",
                sections = listOf(
                    WorkoutDetailSectionType.MAP,
                    WorkoutDetailSectionType.ELEVATION,
                    WorkoutDetailSectionType.CHARTS
                )
            ),
            WorkoutDetailTabConfig(
                id = "tab_zones",
                preset = WorkoutDetailTabPreset.ZONES,
                customTitle = "",
                sections = listOf(
                    WorkoutDetailSectionType.ZONES
                )
            ),
            WorkoutDetailTabConfig(
                id = "tab_laps_segments",
                preset = WorkoutDetailTabPreset.LAPS_AND_SEGMENTS,
                customTitle = "",
                sections = listOf(
                    WorkoutDetailSectionType.LAPS,
                    WorkoutDetailSectionType.STRAVA
                )
            )
        )

        /**
         * Normalizes [sections] by removing duplicates and pinning [WorkoutDetailSectionType.MAP] at index `0` whenever present.
         */
        fun normalizeTabSections(sections: List<WorkoutDetailSectionType>): List<WorkoutDetailSectionType> {
            val distinct = sections.distinct()
            return if (WorkoutDetailSectionType.MAP in distinct) {
                listOf(WorkoutDetailSectionType.MAP) + distinct.filter { it != WorkoutDetailSectionType.MAP }
            } else {
                distinct
            }
        }

        /**
         * Resolves the [WorkoutDetailTabLayoutMode] for a normalized list of sections.
         */
        fun resolveTabLayoutMode(sections: List<WorkoutDetailSectionType>): WorkoutDetailTabLayoutMode {
            val normalized = normalizeTabSections(sections)
            val hasMap = WorkoutDetailSectionType.MAP in normalized
            return when {
                !hasMap -> WorkoutDetailTabLayoutMode.MODE_A_CARD_SCROLL
                normalized.size == 1 -> WorkoutDetailTabLayoutMode.MODE_C_FULL_MAP
                else -> WorkoutDetailTabLayoutMode.MODE_B_SPLIT_MAP
            }
        }

        /**
         * Validates a list of [WorkoutDetailTabConfig]s, enforcing the "Map-at-top" invariant on every tab,
         * removing empty tabs, and falling back to [DEFAULT_TABS] if no valid tabs remain (`REQ-UI-408.5`).
         */
        fun validateConfigs(tabs: List<WorkoutDetailTabConfig>): List<WorkoutDetailTabConfig> =
            tabs.map { it.validateConfig() }
                .filter { it.sections.isNotEmpty() }
                .ifEmpty { DEFAULT_TABS }

        /**
         * Filters [tabs] to retain only those that have at least one active section for the current workout (`REQ-UI-408.4`).
         * Guarantees at least 1 fallback tab is returned even if all sections are empty.
         */
        fun filterVisibleWorkoutDetailTabs(
            tabs: List<WorkoutDetailTabConfig>,
            availability: WorkoutDetailSectionAvailability,
            detailPreferences: WorkoutDetailPreferences = WorkoutDetailPreferences()
        ): List<WorkoutDetailTabConfig> {
            val baseTabs = validateConfigs(tabs)
            val visible = baseTabs.filter { tab ->
                tab.normalizedSections.any { section ->
                    availability.isSectionActive(section, detailPreferences)
                }
            }
            return visible.ifEmpty { listOf(baseTabs.first()) }
        }

        /**
         * Serializes [tabs] into a versioned wire string (`v1|<id>:<preset>:<urlEncodedTitle>:<sec1,sec2>;...`).
         */
        fun serializeTabs(tabs: List<WorkoutDetailTabConfig>): String {
            val validTabs = tabs
                .map { it.copy(sections = it.normalizedSections) }
                .filter { it.sections.isNotEmpty() }
                .ifEmpty { DEFAULT_TABS }

            val body = validTabs.joinToString(";") { tab ->
                val encodedId = URLEncoder.encode(tab.id.ifBlank { "tab" }, StandardCharsets.UTF_8.name())
                val encodedTitle = URLEncoder.encode(tab.customTitle, StandardCharsets.UTF_8.name())
                val sectionNames = tab.normalizedSections.joinToString(",") { it.name }
                "$encodedId:${tab.preset.name}:$encodedTitle:$sectionNames"
            }
            return "$SCHEMA_PREFIX$body"
        }

        /**
         * Deserializes a versioned wire string into a list of [WorkoutDetailTabConfig]s.
         * Safely recovers to [DEFAULT_TABS] on null, blank, truncated, or corrupted inputs (`REQ-UI-408.2`).
         */
        fun deserializeTabs(raw: String?): List<WorkoutDetailTabConfig> {
            if (raw.isNullOrBlank()) return DEFAULT_TABS
            return runCatching {
                val payload = if (raw.startsWith(SCHEMA_PREFIX)) {
                    raw.removePrefix(SCHEMA_PREFIX)
                } else {
                    raw
                }
                if (payload.isBlank()) return DEFAULT_TABS

                val parsedTabs = payload.split(";")
                    .mapNotNull { entry ->
                        val parts = entry.split(":", limit = 4)
                        if (parts.size < 4) return@mapNotNull null
                        val id = URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name()).ifBlank { "tab_custom" }
                        val preset = runCatching { WorkoutDetailTabPreset.valueOf(parts[1].trim()) }
                            .getOrDefault(WorkoutDetailTabPreset.CUSTOM)
                        val customTitle = URLDecoder.decode(parts[2], StandardCharsets.UTF_8.name())
                        val sections = parts[3].split(",")
                            .mapNotNull { token ->
                                val trimmed = token.trim()
                                if (trimmed.isEmpty()) null
                                else runCatching { WorkoutDetailSectionType.valueOf(trimmed) }.getOrNull()
                            }
                        val normalized = normalizeTabSections(sections)
                        if (normalized.isEmpty()) null
                        else WorkoutDetailTabConfig(
                            id = id,
                            preset = preset,
                            customTitle = customTitle,
                            sections = normalized
                        )
                    }
                parsedTabs.ifEmpty { DEFAULT_TABS }
            }.getOrElse { DEFAULT_TABS }
        }

        /**
         * Appends a new custom tab with default section `[DETAILS]`.
         */
        fun addCustomTab(
            tabs: List<WorkoutDetailTabConfig>,
            customTitle: String = "",
            initialSections: List<WorkoutDetailSectionType> = listOf(WorkoutDetailSectionType.DETAILS)
        ): List<WorkoutDetailTabConfig> {
            val nextIndex = tabs.size + 1
            val newId = "tab_custom_${nextIndex}_${System.nanoTime() and 0xFFFF}"
            val newTab = WorkoutDetailTabConfig(
                id = newId,
                preset = WorkoutDetailTabPreset.CUSTOM,
                customTitle = customTitle.trim(),
                sections = normalizeTabSections(initialSections.ifEmpty { listOf(WorkoutDetailSectionType.DETAILS) })
            )
            return tabs + newTab
        }

        /**
         * Renames the tab at [tabIndex].
         */
        fun renameTab(
            tabs: List<WorkoutDetailTabConfig>,
            tabIndex: Int,
            newTitle: String
        ): List<WorkoutDetailTabConfig> {
            if (tabIndex !in tabs.indices) return tabs
            return tabs.mapIndexed { idx, tab ->
                if (idx == tabIndex) tab.copy(customTitle = newTitle) else tab
            }
        }

        /**
         * Moves a tab from [fromIndex] to [toIndex].
         */
        fun moveTab(
            tabs: List<WorkoutDetailTabConfig>,
            fromIndex: Int,
            toIndex: Int
        ): List<WorkoutDetailTabConfig> {
            if (fromIndex !in tabs.indices || toIndex !in tabs.indices || fromIndex == toIndex) return tabs
            val mutable = tabs.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            return mutable
        }

        /**
         * Deletes the tab at [tabIndex] while enforcing a minimum of 1 remaining tab (`REQ-UI-408.5`).
         */
        fun deleteTab(
            tabs: List<WorkoutDetailTabConfig>,
            tabIndex: Int
        ): List<WorkoutDetailTabConfig> {
            if (tabs.size <= 1 || tabIndex !in tabs.indices) return tabs
            return tabs.filterIndexed { idx, _ -> idx != tabIndex }
        }

        /**
         * Toggles [section] inside the tab at [tabIndex], keeping at least 1 section active in the tab
         * and automatically pinning [WorkoutDetailSectionType.MAP] to index `0` when enabled (`REQ-UI-408.5`).
         */
        fun toggleSectionInTab(
            tabs: List<WorkoutDetailTabConfig>,
            tabIndex: Int,
            section: WorkoutDetailSectionType
        ): List<WorkoutDetailTabConfig> {
            if (tabIndex !in tabs.indices) return tabs
            return tabs.mapIndexed { idx, tab ->
                if (idx != tabIndex) {
                    tab
                } else {
                    val current = tab.normalizedSections
                    val updated = if (section in current) {
                        if (current.size <= 1) current else current.filter { it != section }
                    } else {
                        current + section
                    }
                    tab.copy(sections = normalizeTabSections(updated))
                }
            }
        }

        /**
         * Moves a section within the tab at [tabIndex] from [fromSectionIndex] to [toSectionIndex],
         * preserving [WorkoutDetailSectionType.MAP] pinned at index `0` (`REQ-UI-408.5`).
         */
        fun moveSectionInTab(
            tabs: List<WorkoutDetailTabConfig>,
            tabIndex: Int,
            fromSectionIndex: Int,
            toSectionIndex: Int
        ): List<WorkoutDetailTabConfig> {
            if (tabIndex !in tabs.indices) return tabs
            val tab = tabs[tabIndex]
            val current = tab.normalizedSections.toMutableList()
            if (fromSectionIndex !in current.indices || toSectionIndex !in current.indices || fromSectionIndex == toSectionIndex) {
                return tabs
            }
            if (current[fromSectionIndex] == WorkoutDetailSectionType.MAP || current[toSectionIndex] == WorkoutDetailSectionType.MAP) {
                return tabs
            }
            val item = current.removeAt(fromSectionIndex)
            current.add(toSectionIndex, item)
            val normalized = normalizeTabSections(current)
            return tabs.mapIndexed { idx, existing ->
                if (idx == tabIndex) existing.copy(sections = normalized) else existing
            }
        }
    }
}
