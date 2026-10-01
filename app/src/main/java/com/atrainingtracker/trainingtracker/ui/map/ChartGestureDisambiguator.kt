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

package com.atrainingtracker.trainingtracker.ui.map

import kotlin.math.abs

/**
 * Pure directional slope and touch-slop gesture disambiguator for chart surfaces
 * positioned inside scrollable viewports (REQ-UI-226 / ATT-1872).
 *
 * Differentiates intentional horizontal chart scrubbing/pan gestures from vertical viewport
 * scroll gestures, preventing gesture locking in [MapDetailLayout].
 */
object ChartGestureDisambiguator {

    /**
     * Returns `true` if the displacement represents a dominant vertical swipe
     * exceeding the system touch slop, intended for parent viewport scrolling.
     */
    fun isDominantVertical(diffX: Float, diffY: Float, touchSlop: Float): Boolean {
        val absX = abs(diffX)
        val absY = abs(diffY)
        return absY > absX && absY > touchSlop
    }

    /**
     * Returns `true` if the displacement represents a dominant horizontal drag
     * exceeding the system touch slop, intended for chart scrubbing or pan navigation.
     */
    fun isDominantHorizontal(diffX: Float, diffY: Float, touchSlop: Float): Boolean {
        val absX = abs(diffX)
        val absY = abs(diffY)
        return absX >= absY && absX > touchSlop
    }

    /**
     * Returns `true` if the displacement has not exceeded the touch slop in either axis,
     * indicating a stationary touch or pending tap inspection.
     */
    fun isSubSlop(diffX: Float, diffY: Float, touchSlop: Float): Boolean {
        val absX = abs(diffX)
        val absY = abs(diffY)
        return absX <= touchSlop && absY <= touchSlop
    }
}
