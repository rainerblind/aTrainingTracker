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

package com.atrainingtracker.trainingtracker.helpers

/**
 * Data class holding the vertical layout offsets and total canvas height for workout snapshot assembly.
 * (REQ-UI-205 / ATT-1393)
 */
data class SnapshotSectionOffsets(
    val headerTop: Float,
    val mapTop: Float,
    val elevationTop: Float,
    val analyticsTop: Float,
    val footerTop: Float,
    val totalHeight: Int
)

/**
 * Pure calculation engine for workout snapshot layout dimensions, section offsets, and scaling.
 * (REQ-UI-205 / ATT-1393)
 */
object WorkoutSnapshotLayoutCalculator {

    const val DEFAULT_FOOTER_HEIGHT = 125

    /**
     * Calculates the total canvas height required to stack the snapshot components.
     *
     * @param headerHeight Height of the header section in pixels (or 0 if null).
     * @param mapHeight Height of the map section in pixels.
     * @param elevationHeight Height of the elevation profile in pixels (or 0 if null).
     * @param analyticsHeight Height of the analytics cards in pixels (or 0 if null).
     * @param footerHeight Height of the branding footer in pixels.
     * @return Total canvas height in pixels.
     */
    fun calculateTotalHeight(
        headerHeight: Int,
        mapHeight: Int,
        elevationHeight: Int,
        analyticsHeight: Int,
        footerHeight: Int = DEFAULT_FOOTER_HEIGHT
    ): Int {
        return headerHeight.coerceAtLeast(0) +
                mapHeight.coerceAtLeast(0) +
                elevationHeight.coerceAtLeast(0) +
                analyticsHeight.coerceAtLeast(0) +
                footerHeight.coerceAtLeast(0)
    }

    /**
     * Calculates the horizontal proportional scale factor to align a component to the target canvas width.
     *
     * @param targetWidth The base canvas width (typically map width).
     * @param componentWidth The source width of the component.
     * @return Scale multiplier (returns 1.0f if componentWidth <= 0 or targetWidth <= 0).
     */
    fun calculateScaleFactor(targetWidth: Int, componentWidth: Int): Float {
        if (targetWidth <= 0 || componentWidth <= 0) return 1.0f
        return targetWidth.toFloat() / componentWidth.toFloat()
    }

    /**
     * Calculates the vertical top offsets for each sequentially stacked section.
     */
    fun calculateSectionOffsets(
        headerHeight: Int,
        mapHeight: Int,
        elevationHeight: Int,
        analyticsHeight: Int,
        footerHeight: Int = DEFAULT_FOOTER_HEIGHT
    ): SnapshotSectionOffsets {
        val hH = headerHeight.coerceAtLeast(0)
        val mH = mapHeight.coerceAtLeast(0)
        val eH = elevationHeight.coerceAtLeast(0)
        val aH = analyticsHeight.coerceAtLeast(0)
        val fH = footerHeight.coerceAtLeast(0)

        val headerTop = 0f
        val mapTop = headerTop + hH
        val elevationTop = mapTop + mH
        val analyticsTop = elevationTop + eH
        val footerTop = analyticsTop + aH
        val totalHeight = (footerTop + fH).toInt()

        return SnapshotSectionOffsets(
            headerTop = headerTop,
            mapTop = mapTop,
            elevationTop = elevationTop,
            analyticsTop = analyticsTop,
            footerTop = footerTop,
            totalHeight = totalHeight
        )
    }
}
