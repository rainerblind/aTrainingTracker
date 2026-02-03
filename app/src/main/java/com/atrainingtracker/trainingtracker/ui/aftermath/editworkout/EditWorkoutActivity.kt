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
import androidx.lifecycle.ViewModelProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.activities.WorkoutDetailsActivity
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.dialogs.EditFancyWorkoutNameDialog
import com.atrainingtracker.trainingtracker.ui.components.map.MapComponent
import com.atrainingtracker.trainingtracker.ui.components.map.MapContentType
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionViewHolder
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsViewHolder
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaValuesViewHolder
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
    private lateinit var buttonDelete: Button


    // Adapters for Spinners
    private lateinit var sportTypeAdapter: ArrayAdapter<String>
    private lateinit var equipmentAdapter: ArrayAdapter<String>

    private var showAllSportTypes = false
    private var showAllEquipment = false
    private lateinit var sportTypeNameList: MutableList<String>

    private var detailsViewHolder: WorkoutDetailsViewHolder? = null
    private var extremaValuesViewHolder: ExtremaValuesViewHolder? = null
    private var mapComponent: MapComponent? = null


    companion object {
        const val EXTRA_SHOW_DETAILS = "com.atrainingtracker.trainingtracker.SHOW_DETAILS"
        const val EXTRA_SHOW_EXTREMA = "com.atrainingtracker.trainingtracker.SHOW_EXTREMA"
        const val EXTRA_SHOW_MAP = "com.atrainingtracker.trainingtracker.SHOW_MAP"

        private const val MAX_WORKOUT_TIME_TO_SHOW_DELETE_BUTTON = 5*60 // 5 minutes
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the content view
        setContentView(R.layout.edit_workout_modern)

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
        setupSpinnerOnItemSelectedListeners()

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
        buttonDelete = findViewById(R.id.buttonDelete)
    }

    private fun observeViewModel() {
        viewModel.workoutData.observe(this) { workoutData ->
            // This block is called when the initial data is loaded and on every update
            if (workoutData == null) return@observe

            // --- Populate UI with data from ViewModel ---

            // Populate Text Fields
            // Use 'setText' and check if the current text is already the same to avoid cursor jumps
            if (editWorkoutName.text.toString() != workoutData.headerData.workoutName) {
                editWorkoutName.setText(workoutData.headerData.workoutName)
            }
            if (editDescription.text.toString() != workoutData.descriptionData.description) {
                editDescription.setText(workoutData.descriptionData.description)
            }
            if (editGoal.text.toString() != workoutData.descriptionData.goal) {
                editGoal.setText(workoutData.descriptionData.goal)
            }
            if (editMethod.text.toString() != workoutData.descriptionData.method) {
                editMethod.setText(workoutData.descriptionData.method)
            }

            // Populate Checkboxes
            checkboxCommute.isChecked = workoutData.isCommute
            checkboxTrainer.isChecked = workoutData.isTrainer

            // Populate Spinners
            setupSpinners()

            // details, extrema values, and the map.
            detailsViewHolder?.bind(workoutData.detailsData)
            extremaValuesViewHolder?.bind(workoutData.extremaData)
            mapComponent?.bind(workoutData.id, MapContentType.WORKOUT_TRACK)

            // -- visibility of delete button
            // By default, the button is visible
            buttonDelete.visibility = View.VISIBLE

            // when the workout is more than some minutes, the button is not visible
            if (workoutData.activeTime > MAX_WORKOUT_TIME_TO_SHOW_DELETE_BUTTON) {
                buttonDelete.visibility = View.GONE
            }

            // similarly, when tracking, the button is also not visible
            if (TrainingApplication.isTracking()) {
                buttonDelete.visibility = View.GONE
            }
        }

        viewModel.saveFinishedEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { success ->
                if (success) {
                    // This is the classic, correct pattern you wanted!
                    setResult(Activity.RESULT_OK) // Signal success to the calling activity
                    finish() // Close this activity
                } else {
                    Toast.makeText(this, "Error saving workout.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.deleteFinishedEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { success ->
                if (success) {
                    Toast.makeText(this, "Workout deleted", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this, "Error deleting workout", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSpinnerOnItemSelectedListeners() {
        setupSportSpinnerOnItemSelected()
        setupEquipmentSpinnerOnItemSelected()
    }

    private fun setupSpinners() {
        setupSportSpinner()
        setupEquipmentSpinner()
    }



    private fun setupSportSpinnerOnItemSelected() {
        spinnerSportType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

                // First, get the selected sportType
                val selectedSportType = parent?.getItemAtPosition(position) as? String

                // This is the "Show all sport types" item
                if (selectedSportType == getString(R.string.show_all_sport_types)) {  // -> user selected 'show all sports'
                    if (!showAllSportTypes) {
                        showAllSportTypes = true
                        setupSportSpinner()              // Rebuild the spinner with all items
                        spinnerSportType.performClick() // Open the spinner for the user to select again
                    }
                    return // Stop further processing
                }

                // A regular sport type was selected

                val currentSportName = viewModel.workoutData.value?.headerData?.sportName
                if (selectedSportType != currentSportName) {
                    viewModel.updateSportName(selectedSportType)
                    onSportTypeChanged()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No action needed.
            }
        }
    }

    private fun setupEquipmentSpinnerOnItemSelected() {

        spinnerEquipment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

                // First, get the selected equipment
                val selectedEquipment = parent?.getItemAtPosition(position) as? String

                // This is the "Show all equipment" item
                if (selectedEquipment == getString(R.string.equipment_all)
                    || selectedEquipment == getString(R.string.equipment_all_shoes)
                    || selectedEquipment == getString(R.string.equipment_all_bikes)) {  // -> user selected 'show all equipment/shoes/bikes'
                    if (!showAllEquipment) {
                        showAllEquipment = true
                        setupEquipmentSpinner()         // Rebuild the spinner with all items
                        spinnerEquipment.performClick() // Open the spinner for the user to select again
                    }
                    return // Stop further processing
                }

                // A regular equipment was selected

                val currentEquipmentName = viewModel.workoutData.value?.headerData?.equipmentName
                if (selectedEquipment != currentEquipmentName) {
                    viewModel.updateEquipmentName(selectedEquipment)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No action needed.
            }
        }
    }

    private fun onSportTypeChanged() {
        Log.d("EditWorkoutFragment", "Sport type changed. The ViewModel has been notified.")
    }

    private fun setupSportSpinner() {
        val currentWorkoutData = viewModel.workoutData.value
        val bSportType = currentWorkoutData?.headerData?.bSportType
        val avgSpd = currentWorkoutData?.detailsData?.avgSpeedMps
        var currentSportName = currentWorkoutData?.headerData?.sportName

        // first, calculate the list of sport types
        if (showAllSportTypes || bSportType == null) {  // when we have to show all or the sport type is not known, we show all...
            sportTypeNameList =  SportTypeDatabaseManager.getSportTypesUiNameList()
        } else {
            // first, we get a list of sport types based on the basic sport type and the average speed
            sportTypeNameList = SportTypeDatabaseManager.getSportTypesUiNameList(bSportType, avgSpd?.toDouble() ?: 0.0)

            // when the sportName is not yet defined and the list has only one element, this will be selected as the current sport
            if (currentSportName == null && sportTypeNameList.size == 1) {
                viewModel.workoutData.value?.headerData?.sportName = sportTypeNameList[0]
            }

            // when this list is empty or has only one entry, we show all sports.  (Having a list with only the current sport to select from, makes no sense.)
            // similarly, when the current sport is not in the list, we also show all sports.
            if (sportTypeNameList.size <= 1  || !sportTypeNameList.contains(currentSportName)) {
                showAllSportTypes = true
                setupSportSpinner()
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
        val selectionIndex = sportTypeNameList.indexOf(currentSportName).takeIf { it >= 0 } ?: 0
        spinnerSportType.setSelection(selectionIndex)
    }

    private fun setupEquipmentSpinner() {
        val currentBSportType = viewModel.workoutData.value?.headerData?.bSportType ?: BSportType.UNKNOWN
        var currentEquipmentName = viewModel.workoutData.value?.headerData?.equipmentName

        val equipmentDbHelper = EquipmentDbHelper(this)
        var equipmentList: MutableList<String?> = ArrayList<String?>()

        if (showAllEquipment) {
            equipmentList = equipmentDbHelper.getEquipment(currentBSportType)
        } else {
            equipmentList = equipmentDbHelper.getLinkedEquipment(workoutId)

            // when the equipment is not yet known and there is only one entry in the list, this entry will be selected as the current equipment
            if (currentEquipmentName == null && equipmentList.size == 1) {
                viewModel.workoutData.value?.headerData?.equipmentName = equipmentList[0]
            }

            // when the list is empty or has only one entry, we show all equipment.
            // Similarly, when the current equipment is not in the list, we also show all equipment.
            if (equipmentList.size <= 1 || !equipmentList.contains(currentEquipmentName)) {
                showAllEquipment = true
                setupEquipmentSpinner()
                return
            }

            // add the option to select all equipment
            val allEquipmentId = when (currentBSportType) {
                BSportType.RUN -> R.string.equipment_all_shoes
                BSportType.BIKE -> R.string.equipment_all_bikes
                else -> R.string.equipment_all
            }
            equipmentList.add(getString(allEquipmentId))
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
        val selectionIndex = equipmentList.indexOf(currentEquipmentName).takeIf { it >= 0 } ?: 0
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
            viewModel.updateIsCommute(isChecked)
        }

        checkboxTrainer.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateIsTrainer(isChecked)
        }

        buttonSave.setOnClickListener {
            viewModel.saveChanges()
        }

        buttonDelete.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_workout)
            .setMessage(R.string.really_delete_workout)
            .setPositiveButton(R.string.delete_workout) { _, _ ->
                // Tell the ViewModel to delete the workout
                viewModel.deleteWorkout()
            }
            .setNegativeButton(R.string.Cancel, null)
            .show()
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