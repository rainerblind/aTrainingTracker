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