package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Spinner
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.dialogs.EditFancyWorkoutNameDialog
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText


class EditWorkoutFragment : Fragment() {

    private lateinit var viewModel: EditWorkoutViewModel
    private var workoutId: Long = -1

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

    private var showAllSportTypes = false
    private var showAllEquipment = false
    private lateinit var sportTypeNameList: MutableList<String>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retrieve the workoutId from the arguments passed to the fragment
        arguments?.let {
            workoutId = it.getLong(WorkoutSummaries.WORKOUT_ID, -1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the new modern layout
        val view = inflater.inflate(R.layout.edit_workout_modern, container, false)

        // Find all the views by their IDs
        findViews(view)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ensure we have a valid workoutId before proceeding
        if (workoutId == -1L) {
            // Handle error: No workout ID provided. Maybe close the fragment.
            parentFragmentManager.popBackStack()
            return
        }

        // Create the ViewModel using our factory to pass the workoutId
        val factory = EditWorkoutViewModelFactory(requireActivity().application, workoutId)
        viewModel = ViewModelProvider(this, factory).get(EditWorkoutViewModel::class.java)

        // Setup the UI components and listeners
        setupClickListeners()
        setupTextWatchers()
        setupSpinnerOnItemSelectedListeners()

        // Observe the LiveData from the ViewModel
        observeViewModel()
    }

    private fun findViews(view: View) {
        editWorkoutName = view.findViewById(R.id.editWorkoutName)
        buttonAutoName = view.findViewById(R.id.buttonAutoName)
        spinnerSportType = view.findViewById(R.id.spinnerSportType)
        spinnerEquipment = view.findViewById(R.id.spinnerEquipment)
        checkboxCommute = view.findViewById(R.id.checkboxCommute)
        checkboxTrainer = view.findViewById(R.id.checkboxTrainer)
        editDescription = view.findViewById(R.id.editDescription)
        editGoal = view.findViewById(R.id.editGoal)
        editMethod = view.findViewById(R.id.editMethod)
        buttonSave = view.findViewById(R.id.buttonSave)
    }

    private fun observeViewModel() {
        viewModel.workoutData.observe(viewLifecycleOwner) { workoutData ->
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
            requireContext(),
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

        val equipmentDbHelper = EquipmentDbHelper(getActivity())
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
            requireContext(),
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
        buttonSave.setOnClickListener {
            viewModel.saveChanges()
            // Optional: Close the fragment after saving
            parentFragmentManager.popBackStack()
        }

        buttonAutoName.setOnClickListener {
            showFancyWorkoutNameDialog()
        }

        checkboxCommute.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateIsCommute(isChecked)
        }

        checkboxTrainer.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateIsTrainer(isChecked)
        }
    }

    private fun showFancyWorkoutNameDialog() {
        // Observe the list of names from the ViewModel.
        // We use .observe once here to get the data and build the dialog.
        viewModel.fancyNameList.observe(viewLifecycleOwner) { nameList ->
            if (nameList.isNullOrEmpty()) {
                // Handle case where there are no fancy names
                Toast.makeText(requireContext(), "No fancy names available.", Toast.LENGTH_SHORT).show()
                return@observe
            }

            val dialogBuilder = AlertDialog.Builder(requireContext())
            dialogBuilder.setTitle(R.string.choose_auto_name)

            // The adapter for the list view inside the dialog
            val arrayAdapter = ArrayAdapter<String>(
                requireContext(),
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
                    val fancyNameId = WorkoutSummariesDatabaseManager.getFancyNameId(selectedBaseName)
                    val editDialog = EditFancyWorkoutNameDialog.newInstance(fancyNameId)
                    editDialog.show(parentFragmentManager, EditFancyWorkoutNameDialog.TAG)

                    // Dismiss the current dialog and indicate we've handled the long click.
                    dialog.dismiss()
                    return@OnItemLongClickListener true
                }
            }

            dialog.show()
        }
    }

    companion object {
        const val TAG = "EditWorkoutFragment"

        @JvmStatic
        fun newInstance(workoutId: Long): EditWorkoutFragment {
            val fragment = EditWorkoutFragment()
            val args = Bundle().apply {
                putLong(WorkoutSummaries.WORKOUT_ID, workoutId)
            }
            fragment.arguments = args
            return fragment
        }
    }
}