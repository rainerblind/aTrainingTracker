package com.atrainingtracker.banalservice.ui.devices.editdevice

import android.os.Bundle
import android.view.View
import androidx.core.widget.doOnTextChanged
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.RunDeviceUiData
import com.atrainingtracker.banalservice.ui.devices.editdevice.correctcalibrationfactor.CorrectCalibrationFactorRunDialogFragment

/**
 * A specialized DialogFragment for editing the details of a Running device.
 * It inherits all common logic from [BaseEditDeviceFragment].
 */
class EditRunDeviceFragment : BaseEditDeviceFragment<RunDeviceUiData>() {

    companion object {
        const val TAG = "EditRunDeviceFragment"

        @JvmStatic
        fun newInstance(deviceId: Long): EditRunDeviceFragment {
            return EditRunDeviceFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DEVICE_ID, deviceId)
                }
            }
        }
    }

    // --- Overriding Methods from Base Class ---

    override fun bindUi(data: RunDeviceUiData) {
        // 1. Call the parent to bind all common views first.
        super.bindUi(data)

        // 2. Now, handle the UI specific to a Run device.
        // Make the calibration group visible and configure it for a run sensor.
        binding.groupCalibration.root.visibility = View.VISIBLE
        binding.groupCalibration.layoutCalibrationFactor.hint = getString(R.string.devices_calibration_factor)
        if (binding.groupCalibration.etCalibrationFactor.text.toString() != data.calibrationFactor.toString()) {
            binding.groupCalibration.etCalibrationFactor.setText(data.calibrationFactor.toString())
        }

        setupEditCalibrationFactorButton(
            CorrectCalibrationFactorRunDialogFragment.newInstance(
                originalCalibrationFactor = binding.groupCalibration.etCalibrationFactor.text.toString()
            )
        )
    }

    override fun setupEventListeners() {
        // 1. Call the parent to set up common listeners.
        super.setupEventListeners()

        // 2. Set up listeners for run-specific views.
        binding.groupCalibration.etCalibrationFactor.doOnTextChanged { text, _, _, _ ->
            viewModel.onCalibrationFactorChanged(text.toString().toDouble())
        }
    }

}