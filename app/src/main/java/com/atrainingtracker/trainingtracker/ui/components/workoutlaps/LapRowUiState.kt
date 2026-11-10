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

package com.atrainingtracker.trainingtracker.ui.components.workoutlaps

/**
 * Pure immutable UI state model representing a single lap row in the workout laps table.
 *
 * Decouples Compose rendering from formatting, speed/pace math, and badge evaluation (REQ-UI-506.1).
 *
 * @param lapNr Unique lap number identifier.
 * @param displayName Formatted or resolved display name (e.g. "Lap 1", custom name).
 * @param description Optional user description or note.
 * @param timeFormatted Ready-to-render duration string (e.g. "05:12").
 * @param distanceFormatted Ready-to-render distance string with units (e.g. "1.00 km").
 * @param speedPaceFormatted Ready-to-render speed or pace value string (e.g. "4:30" or "25.4").
 * @param badgeEmoji Optional highlight badge emoji ("🐇" for fastest, "🦔" for slowest).
 * @param badgeDescription Optional accessibility content description for the badge.
 * @param onClick Optional click callback when the lap row is tapped.
 */
data class LapRowUiState(
    val lapNr: Long,
    val displayName: String,
    val description: String? = null,
    val timeFormatted: String,
    val distanceFormatted: String,
    val speedPaceFormatted: String,
    val badgeEmoji: String? = null,
    val badgeDescription: String? = null,
    val onClick: (() -> Unit)? = null
)
