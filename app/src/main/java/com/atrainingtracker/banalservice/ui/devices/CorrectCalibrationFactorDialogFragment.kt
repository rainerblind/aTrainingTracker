package com.atrainingtracker.banalservice.ui.devices

import android.app.Dialog
import android.os.Bundle
import android.util.Log
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
    private val originalCalibrationFactorString: String by lazy {
        arguments?.getString(KEY_CALIBRATION_FACTOR_AS_STRING) ?: ""
    }
    private val title: String by lazy {
        arguments?.getString(KEY_TITLE) ?: ""
    }
    private val explanation: String by lazy {
        arguments?.getString(KEY_EXPLANATION) ?: ""
    }
    private val fieldName: String by lazy {
        arguments?.getString(KEY_FIELD_NAME) ?: ""
    }
    private val roundToInt: Boolean by lazy {
        arguments?.getBoolean(KEY_ROUND_TO_INT) ?: false
    }
    private val initialDistance: Double by lazy {
        arguments?.getDouble(KEY_INITIAL_DISTANCE) ?: 21.0
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCorrectCalibrationFactorBinding.inflate(LayoutInflater.from(requireContext()))

        // initialize the distances with the default value.
        binding.etCorrectDistance.setText(initialDistance.toString())
        binding.etMeasuredDistance.setText(initialDistance.toString())
        binding.tvExplainingText.setText(explanation)
        binding.etNewCalibrationFactor.hint = fieldName

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

            val newCalibrationFactor = originalCalibrationFactorString.toDouble() * (correctDistance!! / measuredDistance!!)
            if (roundToInt) {
                binding.etNewCalibrationFactor.setText(newCalibrationFactor.toInt().toString())
            } else {
                binding.etNewCalibrationFactor.setText(newCalibrationFactor.toString())
            }
        } else {
            positiveButton?.isEnabled = false

            binding.etNewCalibrationFactor.setText("")
        }
    }

    private fun handleSave() {
        val newCalibrationFactor = binding.etNewCalibrationFactor.text.toString()
        Log.i(TAG, "Returning new Calibration Factor: $newCalibrationFactor")

        // --- SEND RESULT BACK ---
        setFragmentResult(REQUEST_KEY,
            bundleOf(
                KEY_RESULT_TYPE to RESULT_TYPE_SAVE,
                KEY_CALIBRATION_FACTOR_AS_STRING to newCalibrationFactor
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CorrectCalibrationFactorDialogFragment"

        // --- KEYS FOR FRAGMENT RESULT API ---
        const val REQUEST_KEY = "calibration_request"
        const val KEY_CALIBRATION_FACTOR_AS_STRING = "calibration_factor"
        const val KEY_RESULT_TYPE = "result_type"
        const val RESULT_TYPE_SAVE = "save"

        private const val KEY_TITLE = "title"
        private const val KEY_FIELD_NAME = "field_name"
        private const val KEY_ROUND_TO_INT = "round_to_int"
        private const val KEY_INITIAL_DISTANCE = "default_distance"
        private const val KEY_EXPLANATION = "explanation"



        // --- FACTORY METHOD ---
        fun newInstance(originalCalibrationFactor: String, title: String, explanation: String, fieldName: String, roundToInt: Boolean, defautlDistance: Double): CorrectCalibrationFactorDialogFragment {
            return CorrectCalibrationFactorDialogFragment().apply {
                arguments = bundleOf(
                    KEY_CALIBRATION_FACTOR_AS_STRING to originalCalibrationFactor,
                    KEY_TITLE to title,
                    KEY_EXPLANATION to explanation,
                    KEY_FIELD_NAME to fieldName,
                    KEY_ROUND_TO_INT to roundToInt,
                    KEY_INITIAL_DISTANCE to defautlDistance
                )
            }
        }
    }
}