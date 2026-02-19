package com.atrainingtracker.banalservice.ui.devices.editdevice

import android.app.Dialog
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.view.LayoutInflater
import android.view.View
import android.widget.AutoCompleteTextView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.DeviceUiData
import com.atrainingtracker.banalservice.ui.devices.DevicesViewModel
import com.atrainingtracker.banalservice.ui.devices.editdevice.correctcalibrationfactor.CorrectCalibrationFactorBaseDialogFragment
import com.atrainingtracker.databinding.DialogEditDeviceGenericBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * The base class for editing a device, following a modern state management pattern.
 * It observes a StateFlow from the ViewModel and updates the UI reactively.
 */
abstract class BaseEditDeviceFragment : DialogFragment() {

    companion object {
        const val ARG_DEVICE_ID = "device_id"
        const val TAG = "BaseEditDeviceFragment"
    }

    // --- COMMON MEMBERS ---
    private var _binding: DialogEditDeviceGenericBinding? = null
    protected val binding get() = _binding!! // Allow subclasses to access binding
    protected val viewModel: DevicesViewModel by viewModels()

    protected val deviceId: Long by lazy {
        requireArguments().getLong(ARG_DEVICE_ID)
    }

    private lateinit var equipmentNames: Array<String>
    private lateinit var checkedItems: BooleanArray

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogEditDeviceGenericBinding.inflate(LayoutInflater.from(requireContext()))

        val customTitleView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_title, null)

        val dialog = MaterialAlertDialogBuilder(requireActivity())
            .setCustomTitle(customTitleView)
            .setView(binding!!.root)
            .setPositiveButton(R.string.OK) { _, _ ->
                // The save action is now much simpler
                viewModel.saveChanges(deviceId)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        // Load the initial data when the dialog is created
        viewModel.loadInitialDeviceData(deviceId)

        // Set up listeners for UI interaction
        setupEventListeners()

        observeUiState(customTitleView)

        return dialog
    }

    private fun observeUiState(customTitleView: View) {
        val dialogIcon = customTitleView.findViewById<ImageView>(R.id.dialog_icon)
        val dialogTitle = customTitleView.findViewById<TextView>(R.id.dialog_title)

        viewModel.uiState.observe(this) { deviceUiData ->
            if (deviceUiData != null) {
                // Set common title
                dialogIcon.setImageResource(deviceUiData.deviceTypeIconRes)
                dialogTitle.text = getString(R.string.edit_device)

                // Let the subclass bind its specific views
                @Suppress("UNCHECKED_CAST")
                bindUi(deviceUiData as DeviceUiData)
            }
            else {
                dismissAllowingStateLoss()
            }
        }
    }


    /**
     * This function now lives in the base class and is called automatically.
     * It binds all views that are common to every device type.
     */
    open fun bindUi(data: DeviceUiData) {
        // Assuming your generic layout has these IDs
        binding.tvLastSeen.setText(data.lastSeen)
        binding.ivBatteryStatus.setImageResource(data.batteryStatusIconRes)
        binding.tvManufacturer.setText(data.manufacturer)

        // only update the name if it has changed
        if (binding.etDeviceName.text.toString() != data.deviceName) {
            binding.etDeviceName.setText(data.deviceName)
        }

        // Handle equipment section automatically
        setupEquipmentSection(data, binding.etEquipment)
    }

    /**
     * Sets up listeners for common fields like name and paired status.
     */
    open fun setupEventListeners() {
        binding.etDeviceName.doOnTextChanged { text, _, _, _ ->
            viewModel.onDeviceNameChanged(text.toString())
        }
    }


    // --- COMMON HELPER METHODS  ---

    private fun setupEquipmentSection(data: DeviceUiData, editText: AutoCompleteTextView) {
        val layout = editText.parent.parent as? View
        if (data.availableEquipment.isEmpty()) {
            layout?.visibility = View.GONE
        } else {
            layout?.visibility = View.VISIBLE
            val onEquipment = getString(data.onEquipmentResId)
            (layout as? com.google.android.material.textfield.TextInputLayout)?.hint = onEquipment

            equipmentNames = data.availableEquipment.toTypedArray()
            val linkedEquipmentSet = data.linkedEquipment.toSet()
            checkedItems = data.availableEquipment.map { linkedEquipmentSet.contains(it) }.toBooleanArray()

            updateEquipmentTextField(editText)

            editText.setOnClickListener {
                showMultiChoiceEquipmentDialog(onEquipment, editText)
            }
        }
    }

    protected fun getLinkedEquipment(): List<String> {
        if (!::equipmentNames.isInitialized) return emptyList()
        return equipmentNames.filterIndexed { index, _ -> checkedItems[index] }
    }

    private fun showMultiChoiceEquipmentDialog(title: String, editText: AutoCompleteTextView) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMultiChoiceItems(equipmentNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(R.string.OK) { _, _ ->
                updateEquipmentTextField(editText)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateEquipmentTextField(editText: AutoCompleteTextView) {
        val selectedEquipment = getLinkedEquipment()
        val displayText = if (selectedEquipment.isEmpty()) {
            getString(R.string.none)
        } else {
            selectedEquipment.joinToString(", ")
        }
        editText.setText(displayText)
    }


    protected fun setupEditCalibrationFactorButton(correctCalibrationFactorDialogFragment: CorrectCalibrationFactorBaseDialogFragment) {
        binding.groupCalibration.bEditCalibrationFactor.visibility = View.VISIBLE

        childFragmentManager.setFragmentResultListener(CorrectCalibrationFactorBaseDialogFragment.REQUEST_KEY, this) { _, bundle ->
            bundle.getString(CorrectCalibrationFactorBaseDialogFragment.KEY_CALIBRATION_FACTOR_AS_STRING)?.let {
                binding.groupCalibration.etCalibrationFactor.setText(it)
            }
        }

        binding.groupCalibration.bEditCalibrationFactor.setOnClickListener {
            // Now we use the specialized dialog
            //val dialog = CorrectCalibrationFactorBikeDialogFragment.newInstance(
            //    originalCalibrationFactor = binding.groupCalibration.etCalibrationFactor.text.toString(),
            //)
            correctCalibrationFactorDialogFragment.show(childFragmentManager, "CorrectCalibrationFactorDialog")
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}