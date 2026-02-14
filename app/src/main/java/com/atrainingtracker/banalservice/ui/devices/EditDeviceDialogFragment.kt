package com.atrainingtracker.banalservice.ui.devices

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
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

    private lateinit var dialog: AlertDialog

    private lateinit var customTitleView: View
    private lateinit var dialogIcon: ImageView
    private lateinit var dialogTitle: TextView


    // Handling linked equipment
    // To hold the list of all available equipment names for the dialog
    private lateinit var equipmentNames: Array<String>
    // To track which items are currently checked
    private lateinit var checkedItems: BooleanArray

    private lateinit var deviceDataObserver: Observer<DeviceEditViewData?>

    private var deviceId = -1L

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogEditDeviceBaseBinding.inflate(LayoutInflater.from(context))

        deviceId = requireArguments().getLong(ARG_DEVICE_ID)

        // Custom title: Inflate the custom title view
        customTitleView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_title, null)
        dialogIcon = customTitleView.findViewById<ImageView>(R.id.dialog_icon)
        dialogTitle = customTitleView.findViewById<TextView>(R.id.dialog_title)

        dialog = MaterialAlertDialogBuilder(requireContext())
            .setCustomTitle(customTitleView)
            .setView(binding.root)
            .setPositiveButton(R.string.OK) { _, _ ->
                saveChanges()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        // --- Observer Setup ---
        deviceDataObserver = Observer { deviceData ->
            if (deviceData == null) {
                // Device might have been deleted from another screen
                dismiss()
                return@Observer
            }

            // Populate all the UI fields with the new data
            populateUi(deviceData)
        }

        // Start observing the device data from the ViewModel
        viewModel.getDevice(deviceId).observe(this, deviceDataObserver)

        return dialog
    }

    /**
     * Populates all the UI views with data from the [DeviceRawData] object.
     */
    private fun populateUi(data: DeviceEditViewData) {

        // Custom title: Populate the custom title view
        dialogIcon.setImageResource(data.deviceTypeIconRes)
        dialogTitle.text = getString(R.string.edit_device)

        // --- Populate Common Views ---
        binding.tvLastSeen.setText(data.lastSeen)
        binding.ivBatteryStatus.setImageResource(data.batteryStatusIconRes)
        binding.tvManufacturer.setText(data.manufacturer)

        binding.etDeviceName.setText(data.deviceName)
        binding.cbPaired.isChecked = data.isPaired

        // --- Configure Calibration Section ---
        if (data.calibrationData == null) {
            binding.groupCalibration.root.visibility = View.GONE
        }
        else {
            binding.groupCalibration.root.visibility = View.VISIBLE
            binding.groupCalibration.layoutCalibrationFactor.hint = getString(data.calibrationData.calibrationFactorNameRes)
            binding.groupCalibration.etCalibrationFactor.setText(data.calibrationData?.value ?: "")

            if (!data.calibrationData.showWheelSizeSpinner) {
                binding.groupCalibration.spinnerWheelCircumference.visibility = View.GONE
            }
            else {
                setupWheelCircumferenceSpinner()
            }

            // finally, the edit/correct calibration factor button
            setupEditCalibrationFactorButton(data)
        }

        // --- Configure Power Features Section ---
        if (data.powerFeatures == null) {
            binding.groupPower.root.visibility = View.GONE
        }
        else {
            binding.groupPower.root.visibility = View.VISIBLE
            val featureDisplayList = viewModel.getPowerFeaturesForDisplay(data.powerFeatures)
            populatePowerFeaturesList(binding.groupPower.llPowerSensors, featureDisplayList)
            binding.groupPower.cbDoublePowerBalanceValues.isChecked = data.powerFeatures.doublePowerBalanceValues
            binding.groupPower.cbInvertPowerBalanceValues.isChecked = data.powerFeatures.invertPowerBalanceValues
        }

        // --- Equipment Section ---
        if (data.availableEquipment.isEmpty()) {
            binding.layoutEquipment.visibility = View.GONE
        }
        else {
            binding.layoutEquipment.visibility = View.VISIBLE
            val onEquipment = getString(data.onEquipmentResId ?: R.string.onBikesText)
            binding.layoutEquipment.hint = onEquipment

            // 1. Prepare data for the multi-choice dialog
            equipmentNames = data.availableEquipment.toTypedArray()

            // Create a set of linked equipment for fast lookups
            val linkedEquipmentSet = data.linkedEquipment.toSet()

            // 2. Initialize the checkedItems array
            checkedItems = data.availableEquipment.map { equipmentName ->
                linkedEquipmentSet.contains(equipmentName)
            }.toBooleanArray()

            // 3. Update the text field to show current selection
            updateEquipmentTextField()

            // 4. Set the click listener to show the dialog
            binding.etEquipment.setOnClickListener {
                showMultiChoiceEquipmentDialog(onEquipment)
            }
        }
    }


    // Simple helper function to setup the wheel circumference spinner
    fun setupWheelCircumferenceSpinner() {
        val spinnerWheelCircumference = binding.groupCalibration.spinnerWheelCircumference
        spinnerWheelCircumference.visibility = View.VISIBLE

        // Translate the first entry of the wheel size list
        val wheelSizeNames = viewModel.wheelSizeNames.toMutableList()
        wheelSizeNames[0] = requireContext().getString(R.string.select_wheel_size_prompt)

        if (spinnerWheelCircumference.adapter == null) {
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                wheelSizeNames
            )
            spinnerWheelCircumference.adapter = adapter
        }
        spinnerWheelCircumference.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Position 0 is the hint "Select wheel size...". We don't want to act on it.
                if (position > 0) {
                    // Get the selected circumference value from the ViewModel.
                    val selectedValue = viewModel.getWheelCircumferenceForPosition(position)
                    // Update the calibration factor text field with the selected value.
                    binding.groupCalibration.etCalibrationFactor.setText(selectedValue.toString())
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No action needed here.
            }
        }
    }

    fun setupEditCalibrationFactorButton(data: DeviceEditViewData) {
        val button = binding.groupCalibration.bEditCalibrationFactor
        val etCalibrationFactor = binding.groupCalibration.etCalibrationFactor

        button.visibility = View.VISIBLE

        // Listen for results from the calibration dialog
        childFragmentManager.setFragmentResultListener(CorrectCalibrationFactorDialogFragment.REQUEST_KEY, this) { _, bundle ->
            // Check which button was clicked in the dialog
            val resultType = bundle.getString(CorrectCalibrationFactorDialogFragment.KEY_RESULT_TYPE)
            if (resultType == CorrectCalibrationFactorDialogFragment.RESULT_TYPE_SAVE) {
                // Get the newly calculated value and save it
                val newCalibration = bundle.getString(CorrectCalibrationFactorDialogFragment.KEY_CALIBRATION_FACTOR_AS_STRING)
                etCalibrationFactor.setText(newCalibration)
            }
        }

        button.setOnClickListener {
            // Get the required title and name from the calibration data.
            val correctTitle = requireContext().getString(data.calibrationData!!.correctTitleRes)
            val explanation = requireContext().getString(data.calibrationData!!.calibrationFactorExplanationRes)
            val calibrationFactorName = requireContext().getString(data.calibrationData!!.calibrationFactorNameRes)


            // Create an instance of the dialog fragment to show it
            val dialog = CorrectCalibrationFactorDialogFragment.newInstance(
                etCalibrationFactor.text.toString(),
                correctTitle,
                explanation = explanation,
                calibrationFactorName,
                data.calibrationData.roundToInt,
                data.calibrationData.initialDistanceForCorrection
            )
            // Show the new dialog over the current one.
            dialog.show(childFragmentManager, CorrectCalibrationFactorDialogFragment.TAG)
        }
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
            }
            else {
                textView.setTextAppearance(android.R.style.TextAppearance_Medium)
            }

            container.addView(textView)
        }
    }

    private fun showMultiChoiceEquipmentDialog(title: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMultiChoiceItems(equipmentNames, checkedItems) { dialog, which, isChecked ->
                // This listener is called every time a checkbox is checked or unchecked.
                // 'which' is the index of the item, 'isChecked' is its new state.
                checkedItems[which] = isChecked
            }
            .setPositiveButton(R.string.OK) { dialog, _ ->
                // User confirmed their selection. Update the text field.
                updateEquipmentTextField()
                // TODO: Here you would also update your ViewModel with the new selection
                // viewModel.updateLinkedEquipment( ... )
            }
            .setNegativeButton(R.string.cancel, null) // No action needed on cancel
            .show()
    }

    private fun updateEquipmentTextField() {
        val selectedEquipment = equipmentNames.filterIndexed { index, _ -> checkedItems[index] }

        val displayText = if (selectedEquipment.isEmpty()) {
            getString(R.string.none)
        }
        else {
            selectedEquipment.joinToString(", ")
        }

        binding.etEquipment.setText(displayText)
    }

    private fun saveChanges() {
        val paired = binding.cbPaired.isChecked
        val deviceName = binding.etDeviceName.text.toString()
        val calibrationFactor = binding.groupCalibration.etCalibrationFactor.text.toString()
        val linkedEquipment = equipmentNames.filterIndexed { index, _ -> checkedItems[index] }
        val doublePowerBalanceValues = binding.groupPower.cbDoublePowerBalanceValues.isChecked
        val invertPowerBalanceValues = binding.groupPower.cbInvertPowerBalanceValues.isChecked

        viewModel.saveChanges(deviceId, paired, deviceName, calibrationFactor, linkedEquipment, doublePowerBalanceValues, invertPowerBalanceValues)
    }




    override fun onDestroyView() {
        super.onDestroyView()
        // Clear the binding reference to avoid memory leaks
        _binding = null
    }
}