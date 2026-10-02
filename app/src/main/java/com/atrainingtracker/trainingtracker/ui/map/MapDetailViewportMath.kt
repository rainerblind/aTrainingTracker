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

/**
 * Pure mathematical functions for normalizing and projecting horizontal viewport offsets
 * across decoupled spatial (distance) and temporal (time) domains in [MapDetailLayout] (REQ-UI-232, REQ-UI-234).
 */
object MapDetailViewportMath {

    /**
     * Translates a normalized start progress fraction in [0.0, 1.0 - 1.0/zoomScale]
     * to a concrete domain start offset in meters or seconds.
     *
     * @param fraction Dimensionless start fraction.
     * @param totalSpan Total domain span (meters or seconds).
     * @param zoomScale Viewport horizontal magnification factor (>= 1.0f).
     * @return Clamped domain start value in `[0.0, totalSpan - totalSpan / zoomScale]`.
     */
    fun fractionToDomain(fraction: Double, totalSpan: Double, zoomScale: Float): Double {
        if (totalSpan <= 0.0) return 0.0
        val safeZoom = zoomScale.coerceAtLeast(1.0f)
        val maxStart = (totalSpan - totalSpan / safeZoom).coerceAtLeast(0.0)
        return (fraction * totalSpan).coerceIn(0.0, maxStart)
    }

    /**
     * Translates a concrete domain start offset in meters or seconds to a normalized
     * dimensionless start progress fraction in `[0.0, 1.0 - 1.0/zoomScale]`.
     *
     * @param domainVal Concrete domain start value (meters or seconds).
     * @param totalSpan Total domain span (meters or seconds).
     * @param zoomScale Viewport horizontal magnification factor (>= 1.0f).
     * @return Clamped dimensionless start fraction.
     */
    fun domainToFraction(domainVal: Double, totalSpan: Double, zoomScale: Float): Double {
        if (totalSpan <= 0.0) return 0.0
        val safeZoom = zoomScale.coerceAtLeast(1.0f)
        val maxFraction = (1.0 - 1.0 / safeZoom).coerceAtLeast(0.0)
        return (domainVal / totalSpan).coerceIn(0.0, maxFraction)
    }
}
