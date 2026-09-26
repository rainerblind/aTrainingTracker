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

package com.atrainingtracker.trainingtracker.ui.theme

/**
 * Supported theme modes specifically for the live workout tracking cockpit (REQ-UI-168).
 */
enum class CockpitThemeMode(val id: String) {
    SYSTEM("system"),
    ALWAYS_DARK("always_dark");

    companion object {
        fun fromId(id: String?): CockpitThemeMode =
            entries.find { it.id == id } ?: SYSTEM
    }
}

/**
 * Resolves the effective dark theme state for the workout cockpit.
 *
 * @param mode The user-selected cockpit theme mode.
 * @param isSystemDark Whether the host Android operating system is currently in Dark Mode.
 * @return True if the cockpit should render in Dark Mode (AMOLED Pure Black), false for Light Mode.
 */
fun resolveEffectiveCockpitDarkTheme(
    mode: CockpitThemeMode,
    isSystemDark: Boolean
): Boolean {
    return when (mode) {
        CockpitThemeMode.SYSTEM -> isSystemDark
        CockpitThemeMode.ALWAYS_DARK -> true
    }
}

/**
 * Resolved theme state for the entire workout cockpit layout (REQ-UI-170).
 */
data class CockpitThemeState(
    val darkTheme: Boolean,
    val amoled: Boolean
)

/**
 * Resolves the effective theme state across the entire TrackingTabsScreen layout (REQ-UI-170).
 *
 * Telemetry Views (Pages 1..N in TRACKING mode, or all pages in CONFIGURATION / PREVIEW mode):
 * - Renders in pure AMOLED dark theme if cockpit dark mode is active.
 *
 * Control Tracking View (Page 0 in TRACKING mode):
 * - Strictly follows the host OS / ambient system theme (isSystemDark), keeping Page 0 light when device is in light mode.
 */
fun resolveEffectiveCockpitThemeState(
    screenMode: com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode,
    currentPage: Int,
    cockpitThemeMode: CockpitThemeMode,
    isSystemDark: Boolean
): CockpitThemeState {
    val isTelemetryTab = (screenMode != com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode.TRACKING) || (currentPage > 0)
    val isCockpitDark = resolveEffectiveCockpitDarkTheme(cockpitThemeMode, isSystemDark)
    return if (isTelemetryTab) {
        CockpitThemeState(darkTheme = isCockpitDark, amoled = isCockpitDark)
    } else {
        CockpitThemeState(darkTheme = isSystemDark, amoled = false)
    }
}

