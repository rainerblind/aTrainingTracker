package com.atrainingtracker.banalservice.ui.devices.editdevice.correctcalibrationfactor

import androidx.core.os.bundleOf
import com.atrainingtracker.R

class CorrectBikeCalibrationFactorDialogFragment : BaseCorrectCalibrationFactorDialogFragment() {

    // --- CONCRETE IMPLEMENTATIONS FOR BIKE SENSORS ---
    override val dialogTitleRes: Int = R.string.devices_correct_calibration_factor_title_bike
    override val explanationRes: Int = R.string.devices_correct_calibration_explanation_bike
    override val fieldNameRes: Int = R.string.devices_wheel_circumference
    override val initialDistance: Double = 100.0 // kilometers
    override val roundToInt: Boolean = true

    companion object {
        const val TAG = "CorrectBikeCalibrationFactorDialogFragment"

        fun newInstance(originalCalibrationFactor: String): CorrectBikeCalibrationFactorDialogFragment {
            return CorrectBikeCalibrationFactorDialogFragment().apply {
                arguments = bundleOf(KEY_CALIBRATION_FACTOR_AS_STRING to originalCalibrationFactor)
            }
        }
    }
}