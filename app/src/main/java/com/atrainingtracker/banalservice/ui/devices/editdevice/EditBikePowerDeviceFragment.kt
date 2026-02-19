package com.atrainingtracker.banalservice.ui.devices.editdevice

import android.os.Bundle
import android.view.View
import com.atrainingtracker.banalservice.ui.devices.DeviceUiData

/**
 * A highly specialized DialogFragment for editing a Bike Power Meter.
 * It inherits all logic from [BaseEditBikeDeviceFragment] and adds the ability
 * to manage power-specific features.
 */
class EditBikePowerDeviceFragment : BaseEditBikeDeviceFragment() {

    companion object {
        const val TAG = "EditBikePowerDeviceFragment"

        @JvmStatic
        fun newInstance(deviceId: Long): EditBikePowerDeviceFragment {
            return EditBikePowerDeviceFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DEVICE_ID, deviceId)
                }
            }
        }
    }

    // --- Overriding Methods ---

    override fun bindUi(data: DeviceUiData) {
        // 1. Call the parent to bind all common AND bike-specific views.
        super.bindUi(data)

        // TODO: depending on the power features, we show or dont show the wheel circumference stuff

        // TODO: Show the correction only for bluetooth devices (and only when the power balance feature is available).

        // 2. Now, handle the UI specific to a Bike Power Meter.
        binding.groupPower.root.visibility = View.VISIBLE
        binding.groupPower.cbDoublePowerBalanceValues.isChecked = data.powerFeatures!!.doublePowerBalanceValues
        binding.groupPower.cbInvertPowerBalanceValues.isChecked = data.powerFeatures.invertPowerBalanceValues
    }

    override fun setupEventListeners() {
        // 1. Call the parent to set up common and bike-specific listeners.
        super.setupEventListeners()

        // 2. Set up listeners for the power feature checkboxes.
        binding.groupPower.cbDoublePowerBalanceValues.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onDoublePowerBalanceValuesChanged(isChecked)
        }
        binding.groupPower.cbInvertPowerBalanceValues.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onInvertPowerBalanceValuesChanged(isChecked)
        }
    }
}