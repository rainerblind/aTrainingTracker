package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.add
import androidx.lifecycle.ViewModelProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.dialogs.EditFancyWorkoutNameDialog
import com.atrainingtracker.trainingtracker.ui.aftermath.EquipmentData
import com.atrainingtracker.trainingtracker.ui.aftermath.SportData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutUpdatePayload
import com.atrainingtracker.trainingtracker.ui.components.map.MapComponent
import com.atrainingtracker.trainingtracker.ui.components.map.MapContentType
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
    private var showMap = false

    // UI View References
    private lateinit var editWorkoutName: TextInputEditText
    private lateinit var buttonAutoName: Button
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

    private lateinit var sportTypeNameList: MutableList<String>

    private var detailsViewHolder: WorkoutDetailsViewHolder? = null
    private var extremaValuesViewHolder: ExtremaValuesViewHolder? = null
    private var mapComponent: MapComponent? = null

    private lateinit var sportTypeDatabaseManager: SportTypeDatabaseManager



    companion object {
        private val TAG = EditWorkoutActivity::class.java.simpleName
        private var DEBUG = TrainingApplication.getDebug(true)

        const val EXTRA_SHOW_DETAILS = "com.atrainingtracker.trainingtracker.SHOW_DETAILS"
        const val EXTRA_SHOW_EXTREMA = "com.atrainingtracker.trainingtracker.SHOW_EXTREMA"
        const val EXTRA_SHOW_MAP = "com.atrainingtracker.trainingtracker.SHOW_MAP"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the content view
        setContentView(R.layout.edit_workout)

        sportTypeDatabaseManager = SportTypeDatabaseManager.getInstance(this)

        // Retrieve the parameters from the Intent's extras
        workoutId = intent.getLongExtra(WorkoutSummaries.WORKOUT_ID, -1)
        showDetails = intent.getBooleanExtra(EXTRA_SHOW_DETAILS, false)
        showExtrema = intent.getBooleanExtra(EXTRA_SHOW_EXTREMA, false)
        showMap = intent.getBooleanExtra(EXTRA_SHOW_MAP, false)

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

    private fun findViews() {
        editWorkoutName = findViewById(R.id.editWorkoutName)
        buttonAutoName = findViewById(R.id.buttonAutoName)

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


        val mapView = findViewById<MapView>(R.id.mapView_include)
        if (showMap) {
            val isPlayAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
            if (isPlayAvailable) {
                mapComponent = MapComponent(mapView, this) { workoutId ->
                    TrainingApplication.startTrackOnMapAftermathActivity(this, workoutId);
                }
            } else {
                mapView.visibility = View.GONE
            }
        } else {
            mapView.visibility = View.GONE
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
                        if (DEBUG) Log.d(TAG, "Partial update: Sport and Equipment changed to ${payload.newSportData}")
                        setupSportSpinnerAndOnItemSelected(payload.newSportData)
                    }

                    is WorkoutUpdatePayload.EquipmentDataChanged -> {
                        if (DEBUG) Log.d(TAG, "Partial update: Equipment changed to ${payload.newEquipmentData}")
                        setupEquipmentSpinnerAndOnItemSelected(payload.newEquipmentData)
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

        // Populate Spinners
        setupSportSpinnerAndOnItemSelected(wd.sportData)
        setupEquipmentSpinnerAndOnItemSelected(wd.equipmentData)

        // details and the map.
        detailsViewHolder?.bind(wd.detailsData)
        mapComponent?.bind(workoutId, MapContentType.WORKOUT_TRACK)
    }

    private fun setupSportSpinnerAndOnItemSelected(sportData: SportData) {
        if (DEBUG) Log.i(TAG, "setupSportSpinner called")

        setupSportSpinner(sportData)
        setupSportSpinnerOnItemSelected(sportData)
    }

    private fun setupEquipmentSpinnerAndOnItemSelected(equipmentData: EquipmentData) {
        if (DEBUG) Log.i(TAG, "setupEquipmentSpinner called")

        setupEquipmentSpinner(equipmentData)
        setupEquipmentSpinnerOnItemSelected(equipmentData)
    }

    private fun setupSportSpinnerOnItemSelected(sportData: SportData) {
        spinnerSportType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

                // First, get the selected sportType
                val selectedSportType = parent?.getItemAtPosition(position) as? String
                if (DEBUG) Log.i(TAG, "OnItemSelected: Selected sport type: $selectedSportType")

                // This is the "Show all sport types" item
                if (selectedSportType == getString(R.string.show_all_sport_types)) {  // -> user selected 'show all sports'
                    if (DEBUG) Log.i(TAG, "OnItemSelected: Show all sport types was selected -> reset up the spinner and perform a click.")
                    setupSportSpinner(sportData, true)  // Rebuild the spinner with all items
                    spinnerSportType.performClick()           // Open the spinner for the user to select again
                } else {
                    if (DEBUG) Log.i(TAG, "OnItemSelected: A regular sport type was selected -> update the viewModel")
                    // A regular sport type was selected  -> update the viewModel
                    viewModel.updateSportName(selectedSportType)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No action needed.
            }
        }
    }

    private fun setupEquipmentSpinnerOnItemSelected(newEquipmentData: EquipmentData) {

        spinnerEquipment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

                // First, get the selected equipment
                val selectedEquipment = parent?.getItemAtPosition(position) as? String
                val noEquipmentString = getString(R.string.no_equipment)

                // This is the "Show all equipment" item
                if (selectedEquipment == getString(R.string.equipment_all)
                    || selectedEquipment == getString(R.string.equipment_all_shoes)
                    || selectedEquipment == getString(R.string.equipment_all_bikes)) {  // -> user selected 'show all equipment/shoes/bikes'
                    setupEquipmentSpinner(newEquipmentData,true)         // Rebuild the spinner with all items
                    spinnerEquipment.performClick() // Open the spinner for the user to select again
                }
                else if (selectedEquipment == noEquipmentString) {
                    // User selected "No Equipment", so we update the ViewModel with null.
                    viewModel.updateEquipmentName(null)
                }
                else {
                    // A regular equipment was selected  -> update the view model
                    viewModel.updateEquipmentName(selectedEquipment)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No action needed.
            }
        }
    }

    private fun setupSportSpinner(sportData: SportData, showAllSportTypes: Boolean = false) {
        val newBSportType = sportData.bSportType
        val newAvgSpd = sportData.avgSpeedMps
        val newSportName = sportData.sportName
        if (DEBUG) Log.i(TAG, "Setting up sport spinner. newBSportType=$newBSportType newAvgSpd=$newAvgSpd newSportName=$newSportName, showAllSportTypes=$showAllSportTypes")

        // first, calculate the list of sport types
        if (showAllSportTypes) {  // when we have to show all or the sport type is not known, we show all...
            if (DEBUG) Log.i(TAG, "Setting up sport spinner with all sport types")
            sportTypeNameList =  sportTypeDatabaseManager.getSportTypesUiNameList()
        } else {
            if (DEBUG) Log.i(TAG, "Setting up sport spinner with filtered sport types. newBSportType=$newBSportType newAvgSpd=$newAvgSpd")
            // first, we get a list of sport types based on the basic sport type and the average speed
            sportTypeNameList = sportTypeDatabaseManager.getSportTypesUiNameList(newBSportType, newAvgSpd?.toDouble() ?: 0.0)

            // when the list has only one element, this will be selected as the current sport
            // if (sportTypeNameList.size == 1) {
            //    if (DEBUG) Log.i(TAG, "SetupSportSpinner: List of sports has only one sport type ($sportTypeNameList[0]) -> we inform the viewModel")
            //    viewModel.updateSportName(sportTypeNameList[0])
            // }

            // when this list is empty or has only one entry, we show all sports.  (Having a list with only the current sport to select from, makes no sense.)
            // similarly, when the current sport is not in the list, we also show all sports.
            if (sportTypeNameList.size <= 1  || !sportTypeNameList.contains(newSportName)) {
                if (DEBUG) Log.i(TAG, "SetupSportSpinner: List of sports is empty or has only one entry (or less) -> we show all sports")
                setupSportSpinner(sportData, true)
                return
            }
            sportTypeNameList.add(getString(R.string.show_all_sport_types))
        }

        // Create the adapter and assign it to the spinner
        sportTypeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            sportTypeNameList
        )
        sportTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSportType.adapter = sportTypeAdapter

        // Set the current selection after the adapter has been set
        val selectionIndex = sportTypeNameList.indexOf(newSportName).takeIf { it >= 0 } ?: 0
        spinnerSportType.setSelection(selectionIndex)
    }

    private fun setupEquipmentSpinner(newEquipmentData: EquipmentData, showAllEquipment: Boolean = false) {

        val newBSportType = newEquipmentData.bSportType
        val newEquipmentName = newEquipmentData.equipmentName

        if (DEBUG) Log.i(TAG, "setupEquipmentSpinner. newBSportType=$newBSportType newEquipmentName=$newEquipmentName, showAllEquipment=$showAllEquipment")

        val equipmentDbHelper = EquipmentDbHelper(this)
        var equipmentList: MutableList<String?> = ArrayList<String?>()

        if (showAllEquipment) {
            equipmentList = equipmentDbHelper.getEquipment(newBSportType)
        } else {
            equipmentList = equipmentDbHelper.getLinkedEquipment(workoutId)

            // when the equipment is not yet known and there is only one entry in the list, this entry will be selected as the current equipment
            // if (currentEquipmentName == null && equipmentList.size == 1) {
            //     viewModel.updateEquipmentName(equipmentList[0])
            // }

            // when the list is empty or has only one entry, we show all equipment.
            // Similarly, when the current equipment is not in the list, we also show all equipment.
            if (equipmentList.size <= 1 || !equipmentList.contains(newEquipmentName)) {
                setupEquipmentSpinner(newEquipmentData, true)
                return
            }

            // add the option to select all equipment
            val allEquipmentId = when (newBSportType) {
                BSportType.RUN -> R.string.equipment_all_shoes
                BSportType.BIKE -> R.string.equipment_all_bikes
                else -> R.string.equipment_all
            }
            equipmentList.add(getString(allEquipmentId))
        }

        // Add the "No Equipment" option to the top of the list
        val noEquipmentString = getString(R.string.no_equipment) // Create this string resource
        if (!equipmentList.contains(noEquipmentString)) {
            equipmentList.add(0, noEquipmentString)
        }

        // change visibility depending the the list of equipment
        if (equipmentList.isEmpty()) {
            spinnerEquipment.visibility = View.GONE
            return
        } else {
            spinnerEquipment.visibility = View.VISIBLE
        }

        // Create the adapter and assign it to the spinner
        equipmentAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            equipmentList
        )
        equipmentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEquipment.adapter = equipmentAdapter

        // Set the current selection after the adapter has been set
        val currentEquipment = newEquipmentName ?: noEquipmentString // If current is null, select "No Equipment"
        // val selectionIndex = equipmentList.indexOf(newEquipmentName).takeIf { it >= 0 } ?: 0
        val selectionIndex = equipmentList.indexOf(currentEquipment)
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

        buttonAutoName.setOnClickListener {
            showFancyWorkoutNameDialog()
        }

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