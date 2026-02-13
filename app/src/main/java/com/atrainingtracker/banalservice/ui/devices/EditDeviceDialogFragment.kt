package com.atrainingtracker.banalservice.ui.devices

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.atrainingtracker.R
import com.atrainingtracker.databinding.DialogEditDeviceBaseBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * A modern DialogFragment for editing the details of a connected device.
 * It follows the MVVM pattern, observing data from an [EditDeviceViewModel].
 */
class EditDeviceDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_DEVICE_ID = "device_id"

        const val TAG = "EditDeviceDialogFragment"

        @JvmStatic
        fun newInstance(deviceId: Long): EditDeviceDialogFragment {
            return EditDeviceDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DEVICE_ID, deviceId)
                }
            }
        }
    }

    // Use the ktx delegate to get the ViewModel, scoped to this fragment
    private val viewModel: EditDeviceViewModel by viewModels()

    // View Binding properties
    private var _binding: DialogEditDeviceBaseBinding? = null
    private val binding get() = _binding!!


    private lateinit var deviceDataObserver: Observer<DeviceEditViewData?>

    // A flag to prevent the observer from re-populating editable fields while the user is typing
    private var isInitialPopulation = true

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogEditDeviceBaseBinding.inflate(LayoutInflater.from(context))

        val deviceId = requireArguments().getLong(ARG_DEVICE_ID)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_device)
            .setView(binding.root)
            .setPositiveButton(R.string.OK) { _, _ ->
                // saveChanges()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        // Disable the OK button initially. It will be enabled when data is loaded.
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        }

        // --- Observer Setup ---
        deviceDataObserver = Observer { deviceData ->
            if (deviceData == null) {
                // Device might have been deleted from another screen
                dismiss()
                return@Observer
            }

            // Get the specific UI configuration for this device type
            val config = viewModel.getDialogConfig(deviceData.deviceType)

            // Populate all the UI fields with the new data
            populateUi(config, deviceData)
            isInitialPopulation = false // Mark initial population as complete

            // Now that data is loaded, enable the OK button
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
        }

        // Start observing the device data from the ViewModel
        viewModel.getDevice(deviceId).observe(this, deviceDataObserver)

        return dialog
    }

    /**
     * Populates all the UI views with data from the [DeviceRawData] object.
     */
    private fun populateUi(config: DeviceDialogConfig, data: DeviceRawData) {
        // --- Populate Common Views ---
        binding.tvLastSeen.setText(data.lastSeen)
        binding.ivBatteryStatus.setImageResource(data.batteryStatusIconRes)
        binding.tvManufacturer.setText(data.manufacturer)

        // Only set editable text if it's the first time to avoid overwriting user input
        if (isInitialPopulation) {
            binding.etDeviceName.setText(data.deviceName)
            binding.cbPaired.isChecked = data.isPaired
        }

        // --- Configure Calibration Section ---
        binding.groupCalibration.root.visibility = if (config.showsCalibrationFactor) View.VISIBLE else View.GONE
        if (config.showsCalibrationFactor) {
            // FIX: Access views VIA the nested binding object
            binding.groupCalibration.layoutCalibrationFactor.hint = getString(config.calibrationFactorTitleRes)
            if (isInitialPopulation) {
                binding.groupCalibration.etCalibrationFactor.setText(data.calibrationData?.value ?: "")
            }
            binding.groupCalibration.spinnerWheelCircumference.visibility = if (config.showsWheelCircumferenceSpinner) View.VISIBLE else View.GONE
            if (config.showsWheelCircumferenceSpinner) {
                if (binding.groupCalibration.spinnerWheelCircumference.adapter == null) {
                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_dropdown_item,
                        viewModel.wheelSizeNames
                    )
                    binding.groupCalibration.spinnerWheelCircumference.adapter = adapter
                }
                // binding.groupCalibration.spinnerWheelCircumference.setSelection(data.calibrationData?.selectedWheelSizePosition ?: 0, false)
            }
        }

        // --- Configure Power Features Section ---
        binding.groupPower.root.visibility = if (config is DeviceDialogConfig.BikePower) View.VISIBLE else View.GONE
        if (config is DeviceDialogConfig.BikePower && data.powerFeatures != null) {
            val featureDisplayList = viewModel.getPowerFeaturesForDisplay(data.powerFeatures)
            populatePowerFeaturesList(binding.groupPower.llPowerSensors, featureDisplayList)
            binding.groupPower.cbDoublePowerBalanceValues.isChecked = data.powerFeatures.doublePowerBalanceValues
            binding.groupPower.cbInvertPowerBalanceValues.isChecked = data.powerFeatures.invertPowerBalanceValues
        }

        // First, check if the available equipment list exists in the data.
        if (!data.availableEquipment.isEmpty()) {
            // Initialize the spinner with the available equipment
            binding.spinnerEquipment.setItems(data.availableEquipment)
            // Now that the spinner is initialized, set the selection.
            binding.spinnerEquipment.setSelection(data.linkedEquipment)
        }
        // TODO: remove view...

    }

    /**
     * Dynamically populates the power features list, graying out deemphasized features.
     */
    private fun populatePowerFeaturesList(container: LinearLayout, features: List<PowerFeatureDisplay>) {
        container.removeAllViews() // Clear any previous views
        val inflater = LayoutInflater.from(container.context)
        for (feature in features) {
            val textView = inflater.inflate(android.R.layout.simple_list_item_1, container, false) as TextView
            textView.text = feature.name

            // Apply a different style for deemphasized features
            if (feature.isDeemphasized) {
                textView.setTextAppearance(android.R.style.TextAppearance_Medium)
                textView.setTextColor(context?.getColor(R.color.bright_grey) ?: 0) // Example color
            } else {
                textView.setTextAppearance(android.R.style.TextAppearance_Medium)
            }

            container.addView(textView)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear the binding reference to avoid memory leaks
        _binding = null
    }
}