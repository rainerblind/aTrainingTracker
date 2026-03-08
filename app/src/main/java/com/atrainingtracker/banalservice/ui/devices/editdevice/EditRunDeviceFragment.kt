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

package com.atrainingtracker.banalservice.ui.devices.editdevice

import android.os.Bundle
import android.view.View
import androidx.core.widget.doOnTextChanged
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.banalservice.ui.devices.editdevice.correctcalibrationfactor.CorrectCalibrationFactorRunDialogFragment

/**
 * A specialized DialogFragment for editing the details of a Running device.
 * It inherits all common logic from [BaseEditDeviceFragment].
 */
class EditRunDeviceFragment : BaseEditDeviceFragment() {

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

    override fun bindUi(data: DeviceUiData) {
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