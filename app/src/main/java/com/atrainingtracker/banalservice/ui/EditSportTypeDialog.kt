package com.atrainingtracker.banalservice.ui

import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.SportType
import com.atrainingtracker.trainingtracker.MyHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

class EditSportTypeDialog : DialogFragment() {

    // Use 'by lazy' for non-nullable properties that are initialized in onCreate/onViewCreated
    private val sportTypeId: Long by lazy {
        arguments?.getLong(SPORT_TYPE_ID) ?: -1L
    }

    // It's safer to make views nullable and handle them after onViewCreated
    private var etName: TextInputEditText? = null
    private var etMinAvgSpeed: TextInputEditText? = null
    private var etMaxAvgSpeed: TextInputEditText? = null
    private var spBSportType: AutoCompleteTextView? = null
    private var spStrava: AutoCompleteTextView? = null
    // TODO: Add other spinners here (Runkeeper, TCX, etc.)

    private var spTcx: AutoCompleteTextView? = null
    private var spGc: AutoCompleteTextView? = null

    private var editableSection: View? = null
    private var layoutMinSpeed: TextInputLayout? = null
    private var layoutMaxSpeed: TextInputLayout? = null

    // Override onCreateView to provide a custom layout
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate our modern material layout
        return inflater.inflate(R.layout.dialog_edit_sport_type, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (DEBUG) Log.d(TAG, "onViewCreated")

        // Initialize views
        etName = view.findViewById(R.id.edit_name)
        etMinAvgSpeed = view.findViewById(R.id.edit_min_speed)
        etMaxAvgSpeed = view.findViewById(R.id.edit_max_speed)
        spBSportType = view.findViewById(R.id.spinner_b_sport_type)
        spStrava = view.findViewById(R.id.spinner_strava)
        spTcx = view.findViewById(R.id.spinner_tcx)
        spGc = view.findViewById(R.id.spinner_gc)
        editableSection = view.findViewById(R.id.editable_section)
        layoutMinSpeed = view.findViewById(R.id.layout_min_speed)
        layoutMaxSpeed = view.findViewById(R.id.layout_max_speed)
        val btnSave = view.findViewById<MaterialButton>(R.id.button_save)
        val btnCancel = view.findViewById<MaterialButton>(R.id.button_cancel)

        // Set listeners using Kotlin lambdas
        btnSave.setOnClickListener {
            saveSportTypes()
            dismiss()
        }
        btnCancel.setOnClickListener { dismiss() }

        loadAndPopulateData()
    }

    private fun loadAndPopulateData() {
        // Shared setup for both new and existing items
        dialog?.setTitle(if (sportTypeId == -1L) R.string.add_sport_type else R.string.edit_sport_type)
        val speedUnit = getString(MyHelper.getSpeedUnitNameId())
        layoutMinSpeed?.suffixText = speedUnit
        layoutMaxSpeed?.suffixText = speedUnit

        if (sportTypeId == -1L) {
            // This is a NEW Sport Type
            populateNewSportType()
        } else {
            // This is an EXISTING Sport Type to edit
            populateExistingSportType()
        }
    }

    /**
     * Sets up the dialog with default values for creating a new SportType.
     */
    private fun populateNewSportType() {
        if (DEBUG) Log.d(TAG, "Populating for a new SportType")

        // Set default text for editable fields
        etName?.setText("")
        etMinAvgSpeed?.setText("3.14")
        etMaxAvgSpeed?.setText("4.2")

        // New items are always editable
        editableSection?.isVisible = true

        // Now, setup the spinners with their default (first) values
        val safeContext = context ?: return

        // Basic Sport Type Spinner (default to the first item, e.g., "Generic")
        ArrayAdapter.createFromResource(safeContext, R.array.Basic_Sport_Types, android.R.layout.simple_list_item_1)
            .let { adapter ->
                spBSportType?.setAdapter(adapter)
                spBSportType?.setText(adapter.getItem(0), false) // Default to first item
            }

        // Helper function for setting up a spinner with a default value
        fun setupAutoCompleteWithDefault(view: AutoCompleteTextView?, uiNamesRes: Int) {
            ArrayAdapter.createFromResource(safeContext, uiNamesRes, android.R.layout.simple_list_item_1).let { adapter ->
                view?.setAdapter(adapter)
                view?.setText(adapter.getItem(0), false) // Default to first item
            }
        }

        // Setup all spinners to their default value
        setupAutoCompleteWithDefault(spStrava, R.array.Strava_Sport_Types_UI_Names)
        setupAutoCompleteWithDefault(spTcx, R.array.TCX_Sport_Types)
        setupAutoCompleteWithDefault(spGc, R.array.GC_Sport_Types)
        // TODO: Add other spinners here (Runkeeper, TrainingPeaks, etc.) in the same pattern
    }

    /**
     * Loads data from the database and populates the dialog for an existing SportType.
     */
    private fun populateExistingSportType() {
        if (DEBUG) Log.d(TAG, "Populating for existing SportType ID: $sportTypeId")

        val db = SportTypeDatabaseManager.getInstance(requireContext()).database
        db.query(
            SportType.TABLE, null, "${SportType.C_ID} =? ",
            arrayOf(sportTypeId.toString()), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                // This logic is mostly the same as before
                val bSportType = BSportType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(SportType.BASE_SPORT_TYPE)))
                val uiName = cursor.getString(cursor.getColumnIndexOrThrow(SportType.UI_NAME))
                val minAvgSpeed = cursor.getDouble(cursor.getColumnIndexOrThrow(SportType.MIN_AVG_SPEED))
                val maxAvgSpeed = cursor.getDouble(cursor.getColumnIndexOrThrow(SportType.MAX_AVG_SPEED))

                // Populate Views
                etName?.setText(uiName)
                etMinAvgSpeed?.setText(String.format(Locale.getDefault(), "%.1f", MyHelper.mps2userUnit(minAvgSpeed)))
                etMaxAvgSpeed?.setText(String.format(Locale.getDefault(), "%.1f", MyHelper.mps2userUnit(maxAvgSpeed)))

                // Handle visibility and spinner setup
                val isEditable = SportTypeDatabaseManager.canDelete(sportTypeId)
                editableSection?.isVisible = isEditable
                if (isEditable) {
                    // This spinner setup function already handles loading the correct value from the cursor
                    setupSpinners(cursor, bSportType)
                }
            }
        }
    }

    private fun setupSpinners(cursor: Cursor, bSportType: BSportType) {
        val safeContext = context ?: return

        // Setup Basic Sport Type Spinner
        ArrayAdapter.createFromResource(safeContext, R.array.Basic_Sport_Types, android.R.layout.simple_list_item_1)
            .let { adapter ->
                spBSportType?.setAdapter(adapter)
                spBSportType?.setText(adapter.getItem(bSportType.ordinal), false)
            }

        fun setupAutoComplete(view: AutoCompleteTextView?, uiNamesRes: Int, apiNamesRes: Int, dbColumnName: String) {
            val apiNames = resources.getStringArray(apiNamesRes).toList()
            val currentValue = cursor.getString(cursor.getColumnIndexOrThrow(dbColumnName))
            ArrayAdapter.createFromResource(safeContext, uiNamesRes, android.R.layout.simple_list_item_1).let { adapter ->
                view?.setAdapter(adapter)
                val selectionIndex = apiNames.indexOf(currentValue).takeIf { it != -1 } ?: 0
                view?.setText(adapter.getItem(selectionIndex), false)
            }
        }

        // Helper function for spinners where UI and API names are the same
        fun setupSimpleAutoComplete(view: AutoCompleteTextView?, namesRes: Int, dbColumnName: String) {
            setupAutoComplete(view, namesRes, namesRes, dbColumnName)
        }

        // Setup all spinners
        setupAutoComplete(spStrava, R.array.Strava_Sport_Types_UI_Names, R.array.Strava_Sport_Types_Strava_Names, SportType.STRAVA_NAME)
        setupSimpleAutoComplete(spTcx, R.array.TCX_Sport_Types, SportType.TCX_NAME)
        setupSimpleAutoComplete(spGc, R.array.GC_Sport_Types, SportType.GOLDEN_CHEETAH_NAME)
        // TODO: Setup other spinners (Runkeeper, TrainingPeaks, ...) in the same pattern
    }

    private fun saveSportTypes() {
        val safeContext = context ?: return

        // Helper function to get the selected API name from a spinner
        fun getSelectedApiName(view: AutoCompleteTextView?, apiNamesRes: Int): String? {
            (view?.adapter as? ArrayAdapter<*>)?.let { adapter ->
                val selectedUiName = view.text.toString()
                val position = (0 until adapter.count).firstOrNull { adapter.getItem(it).toString() == selectedUiName }
                if (position != null && position != -1) {
                    return resources.getStringArray(apiNamesRes)[position]
                }
            }
            return null
        }

        // Helper function for spinners where UI and API names are the same
        fun getSelectedSimpleName(view: AutoCompleteTextView?, namesRes: Int): String? {
            return getSelectedApiName(view, namesRes)
        }


        val contentValues = ContentValues().apply {
            put(SportType.UI_NAME, etName?.text.toString())
            put(SportType.MIN_AVG_SPEED, MyHelper.UserUnit2mps(MyHelper.string2Double(etMinAvgSpeed?.text.toString())))
            put(SportType.MAX_AVG_SPEED, MyHelper.UserUnit2mps(MyHelper.string2Double(etMaxAvgSpeed?.text.toString())))

            if (SportTypeDatabaseManager.canDelete(sportTypeId)) {
                // Base Sport Type
                (spBSportType?.adapter as? ArrayAdapter<*>)?.let { adapter ->
                    val selectedUiName = spBSportType?.text.toString()
                    val position = (0 until adapter.count).firstOrNull { adapter.getItem(it).toString() == selectedUiName }
                    if (position != null) {
                        put(SportType.BASE_SPORT_TYPE, BSportType.values()[position].name)
                    }
                }

                // Save all spinners
                put(SportType.STRAVA_NAME, getSelectedApiName(spStrava, R.array.Strava_Sport_Types_Strava_Names))
                put(SportType.TCX_NAME, getSelectedSimpleName(spTcx, R.array.TCX_Sport_Types))
                put(SportType.GOLDEN_CHEETAH_NAME, getSelectedSimpleName(spGc, R.array.GC_Sport_Types))
            }
        }

        val db = SportTypeDatabaseManager.getInstance(safeContext).database
        if (sportTypeId < 0) { // create an entry
            db.insert(SportType.TABLE, null, contentValues)
        } else {
            db.update(SportType.TABLE, contentValues, "${SportType.C_ID}=?", arrayOf(sportTypeId.toString()))
        }

        // Send broadcast
        safeContext.sendBroadcast(Intent(SPORT_TYPE_CHANGED_INTENT).setPackage(safeContext.packageName))
    }

    companion object {
        @JvmField
        val TAG: String = EditSportTypeDialog::class.java.name
        const val SPORT_TYPE_CHANGED_INTENT = "SPORT_TYPE_CHANGED_INTENT"
        private val DEBUG = BANALService.getDebug(false)
        private const val SPORT_TYPE_ID = "SPORT_TYPE_ID"

        @JvmStatic
        fun newInstance(id: Long): EditSportTypeDialog {
            if (DEBUG) Log.i(TAG, "newInstance for id=$id")
            return EditSportTypeDialog().apply {
                arguments = Bundle().apply {
                    putLong(SPORT_TYPE_ID, id)
                }
            }
        }
    }
}