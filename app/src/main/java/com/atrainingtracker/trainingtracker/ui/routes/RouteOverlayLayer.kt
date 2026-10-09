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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.trainingtracker.ui.routes

/**
 * Toggleable overlay layer types for RouteOnMapScreen (REQ-UI-308, TST-UI-268, ATT-2763).
 *
 * The primary selected route line (TTColor.RouteSelected) and Start/End markers are
 * permanently anchored and are NOT represented as toggleable layers.
 */
enum class RouteOverlayLayer {
    CLIMBS,
    SEGMENTS,
    WAYPOINTS
}
