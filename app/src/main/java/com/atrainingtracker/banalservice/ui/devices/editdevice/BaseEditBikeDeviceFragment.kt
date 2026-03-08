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

import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.doOnTextChanged
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.banalservice.ui.devices.editdevice.correctcalibrationfactor.CorrectCalibrationFactorBikeDialogFragment

/**
 * A specialized DialogFragment for editing the details of a Bike device.
 * It inherits all common logic from [BaseEditDeviceFragment].
 */
abstract class BaseEditBikeDeviceFragment : BaseEditDeviceFragment() {

    override fun bindUi(data: DeviceUiData) {
        super.bindUi(data)

        // --- Configure Calibration Section (specific to Bike) ---
        val wheelCircumference = data.wheelCircumference
        binding.groupCalibration.root.visibility = View.VISIBLE
        binding.groupCalibration.layoutCalibrationFactor.hint = getString(R.string.devices_wheel_circumference)
        if (binding.groupCalibration.etCalibrationFactor.text.toString() != wheelCircumference.toString()) {
            binding.groupCalibration.etCalibrationFactor.setText(wheelCircumference.toString())
        }

        setupWheelCircumferenceSpinner()

        setupEditCalibrationFactorButton(
            CorrectCalibrationFactorBikeDialogFragment.newInstance(
                originalCalibrationFactor = binding.groupCalibration.etCalibrationFactor.text.toString()
            )
        )

        // Power features, etc. would be bound here as well
    }

    override fun setupEventListeners() {
        super.setupEventListeners()

        binding.groupCalibration.etCalibrationFactor.doOnTextChanged { text, _, _, _ ->
            viewModel.onWheelCircumferenceChanged(text.toString().toInt())
        }

        // TODO: add listeners for specific views
    }



    // --- BIKE-SPECIFIC HELPER METHODS ---

    private fun setupWheelCircumferenceSpinner() {
        val spinner = binding.groupCalibration.spinnerWheelCircumference
        spinner.visibility = View.VISIBLE

        val wheelSizeNames = viewModel.wheelSizeNames.toMutableList()
        wheelSizeNames[0] = requireContext().getString(R.string.devices_select_wheel_size_prompt)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, wheelSizeNames)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    val selectedValue = viewModel.getWheelCircumferenceForPosition(position)
                    binding.groupCalibration.etCalibrationFactor.setText(selectedValue.toString())
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}