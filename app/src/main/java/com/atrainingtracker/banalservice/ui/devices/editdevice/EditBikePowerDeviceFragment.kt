package com.atrainingtracker.banalservice.ui.devices.editdevice

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.graphics.Typeface
import com.atrainingtracker.banalservice.Protocol
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

        // depending on the power features, we show or don't show the wheel circumference stuff
        if (data.powerFeatures!!.wheelSpeedDataSupported || data.powerFeatures.wheelDistanceDataSupported || data.powerFeatures.wheelRevolutionDataSupported) {
            binding.groupCalibration.root.visibility = View.VISIBLE
        } else {
            binding.groupCalibration.root.visibility = View.GONE
        }

        binding.groupPower.root.visibility = View.VISIBLE
        populatePowerFeaturesList(data)

        // Show the power balance correction only for bluetooth devices (and only when the power balance feature is available).
        if (data.protocol == Protocol.BLUETOOTH_LE && data.powerFeatures.pedalPowerBalanceSupported) {
            binding.groupPower.llPedalPowerBalanceCorrection.visibility = View.VISIBLE
            binding.groupPower.cbDoublePowerBalanceValues.isChecked = data.powerFeatures!!.doublePowerBalanceValues
            binding.groupPower.cbInvertPowerBalanceValues.isChecked = data.powerFeatures.invertPowerBalanceValues
        }
        else {
            binding.groupPower.llPedalPowerBalanceCorrection.visibility = View.GONE
        }
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

    private fun populatePowerFeaturesList(data: DeviceUiData) {
        val featuresContainer = binding.groupPower.llPowerSensors
        // 1. Clear any old views to prevent duplication on re-bind
        featuresContainer.removeAllViews()

        // 2. Get the display-ready list from the ViewModel
        val powerFeaturesForDisplay = viewModel.getPowerFeaturesForDisplay(data.powerFeatures)

        // 3. Add a TextView for each feature
        if (powerFeaturesForDisplay.isNotEmpty()) {
            for (feature in powerFeaturesForDisplay) {
                val textView = TextView(requireContext()).apply {
                    text = feature.name
                    // Apply a different style for de-emphasized features
                    if (feature.isDeemphasized) {
                        setTypeface(null, Typeface.ITALIC)
                        alpha = 0.7f // Make it slightly transparent
                    }
                    // Add some padding for better spacing
                    setPadding(0, 4, 0, 4)
                }
                featuresContainer.addView(textView)
            }
        }
    }
}