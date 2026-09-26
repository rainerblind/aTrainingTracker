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

package com.atrainingtracker.trainingtracker.settings

/**
 * Encapsulates the athlete-configurable presentation options for an athletic zone profile (REQ-UI-172).
 *
 * @property showBackground Whether to tint the tile card background with 12% zone color alpha.
 * @property showLeftBar Whether to render a 6dp solid accent strip on the left edge.
 * @property showRightBar Whether to render a 6dp solid accent strip on the right edge.
 * @property showTextColor Whether to render the primary metric reading in the active zone color.
 */
data class ZoneDisplayOptions(
    val showBackground: Boolean = true,
    val showLeftBar: Boolean = true,
    val showRightBar: Boolean = false,
    val showTextColor: Boolean = false
)
