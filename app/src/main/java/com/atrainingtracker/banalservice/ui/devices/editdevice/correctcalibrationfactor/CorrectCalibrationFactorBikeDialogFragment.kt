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

package com.atrainingtracker.banalservice.ui.devices.editdevice.correctcalibrationfactor

import androidx.core.os.bundleOf
import com.atrainingtracker.R

class CorrectCalibrationFactorBikeDialogFragment : CorrectCalibrationFactorBaseDialogFragment() {

    // --- CONCRETE IMPLEMENTATIONS FOR BIKE SENSORS ---
    override val dialogTitleRes: Int = R.string.devices_correct_calibration_factor_title_bike
    override val explanationRes: Int = R.string.devices_correct_calibration_explanation_bike
    override val fieldNameRes: Int = R.string.devices_wheel_circumference
    override val initialDistance: Double = 100.0 // kilometers
    override val roundToInt: Boolean = true

    companion object {
        const val TAG = "CorrectBikeCalibrationFactorDialogFragment"

        fun newInstance(originalCalibrationFactor: String): CorrectCalibrationFactorBikeDialogFragment {
            return CorrectCalibrationFactorBikeDialogFragment().apply {
                arguments = bundleOf(KEY_CALIBRATION_FACTOR_AS_STRING to originalCalibrationFactor)
            }
        }
    }
}