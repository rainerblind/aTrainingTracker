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

import androidx.annotation.StringRes
import com.atrainingtracker.R

/**
 * Traffic rule orientation handedness for 3D stereo radar audio panning (REQ-TRK-065.4).
 */
enum class RadarTrafficOrientation(@StringRes val titleResId: Int) {
    /** Right-hand traffic (vehicles drive on right, overtake on left). */
    RIGHT_HAND(R.string.radar_traffic_orientation_rht),

    /** Left-hand traffic (vehicles drive on left, overtake on right). */
    LEFT_HAND(R.string.radar_traffic_orientation_lht)
}
