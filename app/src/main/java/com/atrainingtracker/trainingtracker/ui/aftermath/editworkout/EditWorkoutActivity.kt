/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.dialogs.EditFancyWorkoutNameDialog
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutUpdatePayload
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsViewHolder
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaValuesViewHolder
import com.atrainingtracker.trainingtracker.ui.util.EventObserver
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.MapView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText

class EditWorkoutActivity : AppCompatActivity() {

    private lateinit var viewModel: EditWorkoutViewModel
    private var workoutId: Long = -1

    private var showDetails = false
    private var showExtrema = false

    // UI View References
    private lateinit var editWorkoutName: TextInputEditText
    // private lateinit var buttonAutoName: Button NO_MY_LOCATIONS
    private lateinit var spinnerSportType: Spinner
    private lateinit var spinnerEquipment: Spinner
    private lateinit var checkboxCommute: MaterialCheckBox
    private lateinit var checkboxTrainer: MaterialCheckBox
    private lateinit var editDescription: TextInputEditText
    private lateinit var editGoal: TextInputEditText
    private lateinit var editMethod: TextInputEditText
    private lateinit var buttonSave: Button


    // Adapters for Spinners
    private lateinit var sportTypeAdapter: ArrayAdapter<String>
    private lateinit var equipmentAdapter: ArrayAdapter<String>

    private var detailsViewHolder: WorkoutDetailsViewHolder? = null
    private var extremaValuesViewHolder: ExtremaValuesViewHolder? = null

    companion object {
        private val TAG = "EditWorkoutActivity"
        private var DEBUG = TrainingApplication.getDebug(true)

        const val EXTRA_SHOW_DETAILS = "com.atrainingtracker.trainingtracker.SHOW_DETAILS"
        const val EXTRA_SHOW_EXTREMA = "com.atrainingtracker.trainingtracker.SHOW_EXTREMA"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the content view
        setContentView(R.layout.edit_workout)


        // Retrieve the parameters from the Intent's extras
        workoutId = intent.getLongExtra(WorkoutSummaries.WORKOUT_ID, -1)
        showDetails = intent.getBooleanExtra(EXTRA_SHOW_DETAILS, false)
        showExtrema = intent.getBooleanExtra(EXTRA_SHOW_EXTREMA, false)

        // Ensure we have a valid workoutId before proceeding
        if (workoutId == -1L) {
            Toast.makeText(this, "Error: Invalid Workout ID.", Toast.LENGTH_SHORT).show()
            finish() // Close the activity if the ID is missing
            return
        }

        // Find all the views by their IDs
        findViews()

        // Create the ViewModel using our factory to pass the workoutId
        val factory = EditWorkoutViewModelFactory(application, workoutId)
        viewModel = ViewModelProvider(this, factory).get(EditWorkoutViewModel::class.java)


        // Setup the UI components and listeners
        setupClickListeners()
        setupTextWatchers()

        // Observe the LiveData from the ViewModel
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        if (DEBUG) Log.i(TAG, "onResume()")

        getWindow().getDecorView().setKeepScreenOn(TrainingApplication.keepScreenOn());

        if (TrainingApplication.NoUnlocking()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        if (TrainingApplication.forcePortrait()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

    }

    private fun findViews() {
        editWorkoutName = findViewById(R.id.editWorkoutName)

        /* NO_MY_LOCATIONS
        buttonAutoName = findViewById(R.id.buttonAutoName) */

        spinnerSportType = findViewById(R.id.spinnerSportType)
        spinnerEquipment = findViewById(R.id.spinnerEquipment)

        checkboxCommute = findViewById(R.id.checkboxCommute)
        checkboxTrainer = findViewById(R.id.checkboxTrainer)

        editDescription = findViewById(R.id.editDescription)
        editGoal = findViewById(R.id.editGoal)
        editMethod = findViewById(R.id.editMethod)

        val detailsView = findViewById<View>(R.id.workout_details_include)
        if (showDetails) {
            detailsViewHolder = detailsView?.let { WorkoutDetailsViewHolder(it, this) }
        } else {
            detailsView.visibility = View.GONE
        }

        val extremaView = findViewById<View>(R.id.extrema_values_include)
        if (showExtrema) {
            extremaValuesViewHolder = extremaView?.let { ExtremaValuesViewHolder(it) }
        } else {
            extremaView.visibility = View.GONE
        }

        buttonSave = findViewById(R.id.buttonSave)
    }

    private fun observeViewModel() {

        // Observer for the initial load event
        viewModel.initialWorkoutLoaded.observe(this) {  workoutData ->
            // Check if this event is for the workout we care about.
            if (workoutData.id == workoutId) {
                Log.d("EditWorkoutActivity", "Initial data loaded for workout ${workoutData.id}. Setting up all views.")

                initializeAllViews(workoutData)
            }
        }

        // observer for the update payloads
        viewModel.updatePayloads.observe(this, EventObserver { payloads ->
            // The EventObserver gives us the clean List<WorkoutUpdatePayload>
            Log.d("EditWorkoutActivity", "Received partial update with payloads: $payloads")

            // Iterate through the list of changes and apply them to the specific UI part
            payloads.forEach { payload ->
                when (payload) {
                    is WorkoutUpdatePayload.SportDataChanged -> {
                        if (DEBUG) Log.d(TAG, "Partial update: Sport Data changed to ${payload.newSportData}")
                        // ignore this
                    }

                    is WorkoutUpdatePayload.EquipmentDataChanged -> {
                        if (DEBUG) Log.d(TAG, "Partial update: Equipment changed to ${payload.newEquipmentData}")
                        // ignore this
                    }

                    is WorkoutUpdatePayload.HeaderDataChanged -> {
                        if (DEBUG) Log.d(TAG, "Partial update: Header changed")
                        // Only call setText if the content has actually changed.  This prevents the cursor from jumping during user input.
                        if (editWorkoutName.text.toString() != payload.newHeaderData.workoutName) {
                            editWorkoutName.setText(payload.newHeaderData.workoutName)
                        }
                        checkboxCommute.isChecked = payload.newHeaderData.commute
                        checkboxTrainer.isChecked = payload.newHeaderData.trainer
                    }

                    is WorkoutUpdatePayload.DetailsDataChanged -> {
                        if (DEBUG)Log.d(TAG, "Partial update: Details changed")
                        detailsViewHolder?.bind(payload.newDetailsData)

                    }

                    is WorkoutUpdatePayload.ExtremaDataChanged -> {
                        if (DEBUG) Log.d(TAG, "Partial update: Extrema changed")
                        extremaValuesViewHolder?.bind(payload.newExtremaData)
                    }
                }
            }
        })

        viewModel.sportTypeNames.observe(this) { sportTypeNames ->
            setupSportSpinner(sportTypeNames, viewModel.suggestedSportTypeName)
        }

        viewModel.equipmentNames.observe(this) { equipmentNames ->
            setupEquipmentSpinner(equipmentNames, viewModel.suggestedEquipmentName)
        }

        viewModel.openSpinnerEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { type ->
                when (type) {
                    EditWorkoutViewModel.SpinnerType.SPORT -> {
                        spinnerSportType.post {
                            spinnerSportType.performClick()
                        }
                    }
                    EditWorkoutViewModel.SpinnerType.EQUIPMENT -> {
                        spinnerEquipment.post {
                            spinnerEquipment.performClick()
                        }
                    }
                }
            }
        }


        viewModel.saveFinishedEvent.observe(this) { (safedWorkoutId, success) ->
            if (safedWorkoutId == workoutId
                &&  success) {
                setResult(Activity.RESULT_OK) // Signal success to the calling activity
                finish() // Close this activity
            } else {
                Toast.makeText(this, "Error saving workout.", Toast.LENGTH_SHORT).show()
            }
        }

    }

    // Helper fun to populate the UI with the initial WorkoutData
    private fun initializeAllViews(wd: WorkoutData) {

        // Populate Text Fields
        // Use 'setText' and check if the current text is already the same to avoid cursor jumps
        if (editWorkoutName.text.toString() != wd.headerData.workoutName) {
            editWorkoutName.setText(wd.headerData.workoutName)
        }
        if (editDescription.text.toString() != wd.descriptionData.description) {
            editDescription.setText(wd.descriptionData.description)
        }
        if (editGoal.text.toString() != wd.descriptionData.goal) {
            editGoal.setText(wd.descriptionData.goal)
        }
        if (editMethod.text.toString() != wd.descriptionData.method) {
            editMethod.setText(wd.descriptionData.method)
        }

        // Populate Checkboxes
        checkboxCommute.isChecked = wd.headerData.commute
        checkboxTrainer.isChecked = wd.headerData.trainer

        // Setup the on item selected listeners for the spinners
        setupSportSpinnerOnItemSelected()
        setupEquipmentSpinnerOnItemSelected()

        // details and the map.
        detailsViewHolder?.bind(wd.detailsData)
    }

    private fun setupSportSpinnerOnItemSelected() {
        spinnerSportType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

                // First, get the selected sportType
                val selectedSportType = parent?.getItemAtPosition(position) as String
                if (DEBUG) Log.i(TAG, "OnItemSelected: Selected sport type: $selectedSportType")

                // simply inform the view model
                viewModel.updateSportName(selectedSportType)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No action needed.
            }
        }
    }

    private fun setupEquipmentSpinnerOnItemSelected() {

        spinnerEquipment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

                // Simply, get the selected equipment and inform the viewModel
                val selectedEquipment = parent?.getItemAtPosition(position) as String
                if (DEBUG) Log.i(TAG, "OnItemSelected: Selected equipment: $selectedEquipment")
                viewModel.updateEquipmentName(selectedEquipment)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No action needed.
            }
        }
    }

    private fun setupSportSpinner(sportTypeNames: List<String>, sportName: String) {
        if (DEBUG) Log.i(TAG,"Setting up sport spinner, {sportTypeNames: $sportTypeNames, sportName: $sportName}")

        // Create the adapter and assign it to the spinner
        sportTypeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            sportTypeNames
        )
        sportTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSportType.adapter = sportTypeAdapter

        // Set the current selection after the adapter has been set
        val selectionIndex = sportTypeNames.indexOf(sportName).takeIf { it >= 0 } ?: 0
        spinnerSportType.setSelection(selectionIndex)
    }

    private fun setupEquipmentSpinner(equipmentNames: List<String>, equipmentName: String?) {
        if (DEBUG) Log.i(TAG, "setupEquipmentSpinner, {equipmentNames: $equipmentNames, equipmentName: $equipmentName}")

        // change visibility depending the the list of equipment
        if (equipmentNames.isEmpty()) {
            spinnerEquipment.visibility = View.GONE
            return
        } else {
            spinnerEquipment.visibility = View.VISIBLE
        }

        // Create the adapter and assign it to the spinner
        equipmentAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            equipmentNames
        )
        equipmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEquipment.adapter = equipmentAdapter

        val selectionIndex = equipmentNames.indexOf(equipmentName).takeIf { it >= 0 } ?: 0
        if (DEBUG) Log.i(TAG, "setupEquipmentSpinner, {selectionIndex: $selectionIndex}")
        spinnerEquipment.setSelection(selectionIndex)
    }



    private fun setupTextWatchers() {
        editWorkoutName.doOnTextChanged { text, _, _, _ ->
            // Notify the ViewModel of the name change
            viewModel.updateWorkoutName(text.toString())
        }
        editDescription.doOnTextChanged { text, _, _, _ ->
            // Notify the ViewModel of the description change
            viewModel.updateDescription(text.toString())
        }
        editGoal.doOnTextChanged { text, _, _, _ ->
            // Notify the ViewModel of the goal change
            viewModel.updateGoal(text.toString())
        }
        editMethod.doOnTextChanged { text, _, _, _ ->
            // Notify the ViewModel of the method change
            viewModel.updateMethod(text.toString())
        }
    }

    private fun setupClickListeners() {

        /* NO_MY_LOCATIONS
        buttonAutoName.setOnClickListener {
            showFancyWorkoutNameDialog()
        }
         */

        checkboxCommute.setOnCheckedChangeListener { _, isChecked ->
            // Tell the ViewModel about the change
            viewModel.updateIsCommute(isChecked)

            // If Commute is checked, uncheck Trainer.
            if (isChecked && checkboxTrainer.isChecked) {
                checkboxTrainer.isChecked = false
            }
        }

        checkboxTrainer.setOnCheckedChangeListener { _, isChecked ->
            // Tell the ViewModel about the change
            viewModel.updateIsTrainer(isChecked)

            // If Trainer is checked, uncheck Commute.
            if (isChecked && checkboxCommute.isChecked) {
                checkboxCommute.isChecked = false
            }
        }

        buttonSave.setOnClickListener {
            viewModel.saveChanges()
        }
    }

    private fun showFancyWorkoutNameDialog() {
        // Observe the list of names from the ViewModel.
        // We use .observe once here to get the data and build the dialog.
        viewModel.fancyNameList.observe(this) { nameList ->
            if (nameList.isNullOrEmpty()) {
                // Handle case where there are no fancy names
                Toast.makeText(this, "No fancy names available.", Toast.LENGTH_SHORT).show()
                return@observe
            }

            val dialogBuilder = AlertDialog.Builder(this)
            dialogBuilder.setTitle(R.string.choose_auto_name)

            // The adapter for the list view inside the dialog
            val arrayAdapter = ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                nameList
            )

            dialogBuilder.setAdapter(arrayAdapter) { dialog, which ->
                // The 'which' parameter gives us the position of the clicked item.
                val selectedBaseName = nameList[which]

                // Short Click: Tell the ViewModel which name was selected.
                viewModel.onFancyNameSelected(selectedBaseName)
                dialog.dismiss()
            }

            val dialog = dialogBuilder.create()

            // --- Handling the Long Click ---
            // We need to access the ListView to set a long click listener.
            dialog.listView?.let { listView ->
                listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
                    val selectedBaseName = nameList[position]

                    // Replicate the classic behavior: open the edit dialog.
                    val fancyNameId = WorkoutSummariesDatabaseManager.getInstance(this).getFancyNameId(selectedBaseName)
                    val editDialog = EditFancyWorkoutNameDialog.newInstance(fancyNameId)
                    editDialog.show(supportFragmentManager, EditFancyWorkoutNameDialog.TAG)

                    // Dismiss the current dialog and indicate we've handled the long click.
                    dialog.dismiss()
                    return@OnItemLongClickListener true
                }
            }

            dialog.show()
        }
    }

}