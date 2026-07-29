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

package com.atrainingtracker.trainingtracker.ui.util

/**
 * Encapsulates a single technical phase of a background process (ATT-382).
 */
data class ProgressPhase(
    val id: Int,
    val message: String,
    val progress: Float
)

/**
 * Standardized progress notification model supporting multiple concurrent phases (ATT-346/361/382).
 */
data class MigrationStatus(
    val title: String,
    val phases: List<ProgressPhase>
)
