package com.atrainingtracker.banalservice.ui.devices

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.atrainingtracker.R
import com.atrainingtracker.databinding.DialogCorrectCalibrationFactorBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CorrectCalibrationFactorDialogFragment : DialogFragment() {

    private var _binding: DialogCorrectCalibrationFactorBinding? = null
    private val binding get() = _binding!!

    private var positiveButton: Button? = null

    // The original calibration value is now retrieved from arguments
    private val originalCalibrationFactor: Double by lazy {
        arguments?.getDouble(ARG_CALIBRATION_VALUE) ?: 1.0
    }
    private val title: String by lazy {
        arguments?.getString(ARG_TITLE) ?: ""
    }
    private val fieldName: String by lazy {
        arguments?.getString(ARG_FIELD_NAME) ?: ""
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCorrectCalibrationFactorBinding.inflate(LayoutInflater.from(requireContext()))

        // TODO: set the hint for the calibration factor field

        val builder = MaterialAlertDialogBuilder(requireActivity())
            .setTitle(title)
            .setView(binding.root)
            .setPositiveButton(R.string.OK) { _, _ ->
                handleSave() // Send result back
            }
            .setNegativeButton(R.string.cancel, null)

        val dialog = builder.create()

        dialog.setOnShowListener {
            positiveButton = (it as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
            setupListeners()
            updateUiState()
        }

        return dialog
    }

    private fun setupListeners() {
        binding.etMeasuredDistance.doOnTextChanged { _, _, _, _ -> updateUiState() }
        binding.etCorrectDistance.doOnTextChanged { _, _, _, _ -> updateUiState() }
    }

    private fun updateUiState() {
        val measuredText = binding.etMeasuredDistance.text.toString()
        val correctText = binding.etCorrectDistance.text.toString()

        val measuredDistance = measuredText.toDoubleOrNull()
        val correctDistance = correctText.toDoubleOrNull()

        val isMeasuredValid = measuredDistance != null && measuredDistance > 0
        val isCorrectValid = correctDistance != null
        val canCalculate = isMeasuredValid && isCorrectValid


        // binding.tilMeasuredDistance.error = if (measuredText.isNotEmpty() && !isMeasuredValid) getString(R.string.error_invalid_number_or_zero) else null
        // binding.tilCorrectDistance.error = if (correctText.isNotEmpty() && !isCorrectValid) getString(R.string.error_invalid_number) else null

        if (canCalculate) {
            positiveButton?.isEnabled = true

            val newCalibrationFactor = originalCalibrationFactor * (correctDistance!! / measuredDistance!!)
            binding.tvNewCalibrationFactor.text = newCalibrationFactor.toString()
        } else {
            positiveButton?.isEnabled = false

            binding.tvNewCalibrationFactor.text = ""
        }
    }

    private fun handleSave() {
        val measuredDistance = binding.etMeasuredDistance.text.toString().toDoubleOrNull()
        val correctDistance = binding.etCorrectDistance.text.toString().toDoubleOrNull()

        if (measuredDistance == null || correctDistance == null || measuredDistance == 0.0) return

        val newCalibrationFactor = binding.tvNewCalibrationFactor.text.toString()

        // --- SEND RESULT BACK ---
        setFragmentResult(REQUEST_KEY, bundleOf(
            KEY_RESULT_TYPE to RESULT_TYPE_SAVE,
            KEY_CALIBRATION_VALUE to newCalibrationFactor
        )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SetCalibrationDialogFragment"
        // --- KEYS FOR FRAGMENT RESULT API ---
        const val REQUEST_KEY = "calibration_request"
        const val KEY_CALIBRATION_VALUE = "calibration_value"
        const val KEY_RESULT_TYPE = "result_type"
        const val RESULT_TYPE_SAVE = "save"

        private const val ARG_CALIBRATION_VALUE = "original_calibration_value"
        private const val ARG_TITLE = "title"
        private const val ARG_FIELD_NAME = "field_name"


        // --- FACTORY METHOD ---
        fun newInstance(originalCalibrationFactor: Double, title: String, fieldName: String): CorrectCalibrationFactorDialogFragment {
            return CorrectCalibrationFactorDialogFragment().apply {
                arguments = bundleOf(ARG_CALIBRATION_VALUE to originalCalibrationFactor,
                    ARG_TITLE to title,
                    ARG_FIELD_NAME to fieldName)
            }
        }
    }
}