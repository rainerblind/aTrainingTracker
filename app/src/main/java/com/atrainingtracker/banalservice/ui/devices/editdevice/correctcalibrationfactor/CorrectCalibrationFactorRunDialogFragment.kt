package com.atrainingtracker.banalservice.ui.devices.editdevice.correctcalibrationfactor

import androidx.core.os.bundleOf
import com.atrainingtracker.R

class CorrectCalibrationFactorRunDialogFragment : CorrectCalibrationFactorBaseDialogFragment() {

    // --- CONCRETE IMPLEMENTATIONS FOR RUN SENSORS ---
    override val dialogTitleRes: Int = R.string.devices_calibration_factor
    override val explanationRes: Int = R.string.devices_correct_calibration_explanation_run
    override val fieldNameRes: Int = R.string.devices_correct_calibration_factor_title_run
    override val initialDistance: Double = 42.195 // kilometers
    override val roundToInt: Boolean = false

    companion object {
        const val TAG = "CorrectRunCalibrationFactorDialogFragment"

        fun newInstance(originalCalibrationFactor: String): CorrectCalibrationFactorRunDialogFragment {
            return CorrectCalibrationFactorRunDialogFragment().apply {
                arguments = bundleOf(KEY_CALIBRATION_FACTOR_AS_STRING to originalCalibrationFactor)
            }
        }
    }
}