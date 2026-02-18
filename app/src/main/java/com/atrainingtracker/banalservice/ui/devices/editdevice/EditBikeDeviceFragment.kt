package com.atrainingtracker.banalservice.ui.devices.editdevice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.doOnTextChanged
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.BikeDeviceUiData
import com.atrainingtracker.banalservice.ui.devices.editdevice.correctcalibrationfactor.CorrectCalibrationFactorBaseDialogFragment
import com.atrainingtracker.banalservice.ui.devices.editdevice.correctcalibrationfactor.CorrectCalibrationFactorBikeDialogFragment
import com.atrainingtracker.databinding.DialogEditDeviceGenericBinding

/**
 * A specialized DialogFragment for editing the details of a Bike device.
 * It inherits all common logic from [BaseEditDeviceFragment].
 */
class EditBikeDeviceFragment : BaseEditDeviceFragment<BikeDeviceUiData>() {

    companion object {
        const val TAG = "EditBikeDeviceFragment"

        @JvmStatic
        fun newInstance(deviceId: Long): EditBikeDeviceFragment {
            return EditBikeDeviceFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DEVICE_ID, deviceId)
                }
            }
        }
    }

    // --- IMPLEMENTING ABSTRACT MEMBERS ---

    // override val layoutId: Int = R.layout.dialog_edit_device_generic

    override fun bindUi(data: BikeDeviceUiData) {
        super.bindUi(data)

        // --- Configure Calibration Section (specific to Bike) ---
        val wheelCircumference = data.wheelCircumference
        binding.groupCalibration.root.visibility = View.VISIBLE
        binding.groupCalibration.layoutCalibrationFactor.hint = getString(R.string.devices_wheel_circumference)
        binding.groupCalibration.etCalibrationFactor.setText(wheelCircumference.toString())

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
            viewModel.onWheelCircumferenceChanged(deviceId, text.toString().toDoubleOrNull())
        }

        // TODO: add listeners for specific views
    }



    // --- BIKE-SPECIFIC HELPER METHODS ---

    private fun setupWheelCircumferenceSpinner() {
        val spinner = binding.groupCalibration.spinnerWheelCircumference
        spinner.visibility = View.VISIBLE

        val wheelSizeNames = viewModel.wheelSizeNames.toMutableList()
        wheelSizeNames[0] = requireContext().getString(R.string.select_wheel_size_prompt)

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