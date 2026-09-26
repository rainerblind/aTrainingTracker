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
