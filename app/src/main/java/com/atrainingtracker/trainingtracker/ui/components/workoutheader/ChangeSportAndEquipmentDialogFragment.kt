package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper

class ChangeSportAndEquipmentDialogFragment : DialogFragment() {

    private val TAG = "ChangeSportAndEquipment"

    // Modern, lambda-based listener that passes back the selected IDs.
    // The fragment is responsible for the database logic.
    var onSave: ((newSportId: Long, newEquipmentId: Long) -> Unit)? = null

    // Arguments are loaded safely and lazily.
    private val initialSportId: Long by lazy { requireArguments().getLong(ARG_CURRENT_SPORT_ID) }
    private val initialEquipmentName: String? by lazy { requireArguments().getString(ARG_CURRENT_EQUIPMENT_NAME) }

    // Views
    private lateinit var sportSpinner: Spinner
    private lateinit var equipmentSpinner: Spinner
    private lateinit var equipmentLabel: TextView

    // Data
    private val sportTypeIds: List<Long> by lazy { SportTypeDatabaseManager.getInstance(requireContext()).getSportTypesIdList() }
    private val sportTypeNames: List<String> by lazy { SportTypeDatabaseManager.getInstance(requireContext()).getSportTypesUiNameList() }
    private lateinit var equipmentDbHelper: EquipmentDbHelper

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_change_sport, null)
        equipmentDbHelper = EquipmentDbHelper(requireContext())

        // Initialize Views
        sportSpinner = view.findViewById(R.id.spinner_change_sport)
        equipmentSpinner = view.findViewById(R.id.spinner_change_equipment)
        equipmentLabel = view.findViewById(R.id.tv_equipment_label)

        setupSportSpinner()
        setupEquipmentSpinner() // Initial setup for equipment spinner adapter

        // Set initial selections and listeners
        setInitialSportSelection()
        updateEquipmentSpinner(initialSportId, initialEquipmentName)
        setupSportSelectionListener()

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setTitle(R.string.change_sport)
            .setPositiveButton(R.string.save) { _, _ ->
                handleSave()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun setupSportSpinner() {
        sportSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            sportTypeNames
        )
    }

    private fun setInitialSportSelection() {
        val selectionIndex = sportTypeIds.indexOf(initialSportId)
        if (selectionIndex != -1) {
            // Set selection WITHOUT triggering the onItemSelected listener initially.
            sportSpinner.setSelection(selectionIndex, false)
        }
    }

    private fun setupEquipmentSpinner() {
        // Set up the adapter once with an empty list. It will be populated by updateEquipmentSpinner.
        equipmentSpinner.adapter = ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            mutableListOf()
        )
    }

    private fun setupSportSelectionListener() {
        sportSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedSportId = sportTypeIds.getOrElse(position) { return }
                // When a new sport is selected, reset the equipment selection.
                val equipmentToSelect = if (selectedSportId == initialSportId) initialEquipmentName else null
                updateEquipmentSpinner(selectedSportId, equipmentToSelect)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateEquipmentSpinner(sportId: Long, equipmentToSelect: String?) {
        val bSportType = SportTypeDatabaseManager.getInstance(requireContext()).getBSportType(sportId);

        // Set the equipment label based on sport type
        equipmentLabel.setText(
            when (bSportType) {
                BSportType.BIKE -> R.string.equipment_type_bike
                BSportType.RUN -> R.string.equipment_type_shoe
                else -> R.string.Equipment
            }
        )

        val equipmentNames = equipmentDbHelper.getEquipment(bSportType)
        val hasEquipment = equipmentNames.isNotEmpty()

        // Use isVisible for cleaner visibility toggling
        equipmentLabel.isVisible = hasEquipment
        equipmentSpinner.isVisible = hasEquipment

        if (hasEquipment) {
            val adapter = equipmentSpinner.adapter as ArrayAdapter<String>
            adapter.clear()
            adapter.addAll(equipmentNames)
            adapter.notifyDataSetChanged()

            if (equipmentToSelect != null) {
                val equipmentIndex = equipmentNames.indexOf(equipmentToSelect)
                if (equipmentIndex != -1) {
                    equipmentSpinner.setSelection(equipmentIndex)
                }
            }
        }
    }

    private fun handleSave() {
        val selectedSportId = sportTypeIds.getOrElse(sportSpinner.selectedItemPosition) {
            Log.e(TAG, "Invalid sport selection on save.")
            return
        }

        // Only get equipment if the spinner is visible
        val selectedEquipmentId = if (equipmentSpinner.isVisible && equipmentSpinner.selectedItem != null) {
            val selectedEquipmentName = equipmentSpinner.selectedItem.toString()
            equipmentDbHelper.getEquipmentId(selectedEquipmentName)
        } else {
            0L // Or your designated ID for "no equipment"
        }

        Log.d(TAG, "Saving SportID: $selectedSportId, EquipmentID: $selectedEquipmentId")

        // Pass the result back to the caller. The dialog is "dumb" and does not save.
        onSave?.invoke(selectedSportId, selectedEquipmentId)
    }

    companion object {
        private const val ARG_CURRENT_SPORT_ID = "currentSportId"
        private const val ARG_CURRENT_EQUIPMENT_NAME = "currentEquipmentName"

        @JvmStatic
        fun newInstance(currentSportId: Long, currentEquipmentName: String?): ChangeSportAndEquipmentDialogFragment {
            return ChangeSportAndEquipmentDialogFragment().apply {
                arguments = bundleOf(
                    ARG_CURRENT_SPORT_ID to currentSportId,
                    ARG_CURRENT_EQUIPMENT_NAME to currentEquipmentName
                )
            }
        }
    }
}