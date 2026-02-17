package com.atrainingtracker.banalservice.ui.devices.editdevice.correctcalibrationfactor

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

/**
 * An abstract base class for a dialog that corrects a calibration factor.
 * It contains all the common UI logic for calculating a new factor.
 * Subclasses must provide the specific configuration (titles, behavior, etc.).
 */
abstract class BaseCorrectCalibrationFactorDialogFragment : DialogFragment() {

    private var _binding: DialogCorrectCalibrationFactorBinding? = null
    private val binding get() = _binding!!

    private var positiveButton: Button? = null

    // --- ABSTRACT PROPERTIES TO BE IMPLEMENTED BY SUBCLASSES ---
    abstract val dialogTitleRes: Int
    abstract val explanationRes: Int
    abstract val fieldNameRes: Int
    abstract val initialDistance: Double
    abstract val roundToInt: Boolean

    // --- COMMON PROPERTIES ---
    private val originalCalibrationFactorString: String by lazy {
        arguments?.getString(KEY_CALIBRATION_FACTOR_AS_STRING) ?: "1.0"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCorrectCalibrationFactorBinding.inflate(LayoutInflater.from(requireContext()))

        // Use abstract properties to configure the UI
        binding.etCorrectDistance.setText(initialDistance.toString())
        binding.etMeasuredDistance.setText(initialDistance.toString())
        binding.tvExplainingText.text = getString(explanationRes)
        binding.etNewCalibrationFactor.hint = getString(fieldNameRes)

        val dialog = MaterialAlertDialogBuilder(requireActivity())
            .setTitle(dialogTitleRes)
            .setView(binding.root)
            .setPositiveButton(R.string.OK) { _, _ -> handleSave() }
            .setNegativeButton(R.string.cancel, null)
            .create()

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
        val measuredDistance = binding.etMeasuredDistance.text.toString().toDoubleOrNull()
        val correctDistance = binding.etCorrectDistance.text.toString().toDoubleOrNull()
        val originalFactor = originalCalibrationFactorString.toDoubleOrNull() ?: return

        val canCalculate = measuredDistance != null && measuredDistance > 0 && correctDistance != null

        if (canCalculate) {
            positiveButton?.isEnabled = true
            val newCalibrationFactor = originalFactor * (correctDistance!! / measuredDistance!!)

            if (roundToInt) {
                binding.etNewCalibrationFactor.setText(newCalibrationFactor.toInt().toString())
            } else {
                binding.etNewCalibrationFactor.setText(String.format("%.4f", newCalibrationFactor))
            }
        } else {
            positiveButton?.isEnabled = false
            binding.etNewCalibrationFactor.setText("")
        }
    }

    private fun handleSave() {
        val newCalibrationFactor = binding.etNewCalibrationFactor.text.toString()
        setFragmentResult(
            REQUEST_KEY,
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
        const val REQUEST_KEY = "calibration_request"
        const val KEY_CALIBRATION_FACTOR_AS_STRING = "calibration_factor"
        const val KEY_RESULT_TYPE = "result_type"
        const val RESULT_TYPE_SAVE = "save"
    }
}