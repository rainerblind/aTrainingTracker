package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.aftermath.DeletionProgress
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * A fragment that displays a list of workout summaries using a modern,
 * ViewModel-driven architecture with a RecyclerView.
 */
class WorkoutSummariesListFragment : Fragment() {

    // Use the Kotlin property delegate for a cleaner ViewModel initialization.
    private val viewModel: WorkoutSummariesViewModel by viewModels()

    private lateinit var workoutAdapter: WorkoutSummariesAdapter
    private lateinit var recyclerView: RecyclerView

    private lateinit var progressContainer: View // Will hold the ProgressBar and TextView
    private lateinit var progressText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()

        // Programmatically create the RecyclerView, similar to the Java version.
        recyclerView = RecyclerView(context).apply {
            id = View.generateViewId() // For state restoration
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setHasFixedSize(true) // Important for performance
            layoutManager = LinearLayoutManager(context)
        }

        // --- Create a container for the progress indicators ---
        progressContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE // Initially hidden

            setBackgroundResource(R.drawable.progress_container_background)

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )

            // Add some padding inside the container
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)

            // Add the ProgressBar to the container
            addView(ProgressBar(context))

            // Add the TextView for progress text
            progressText = TextView(context).apply {
                setTextAppearance(android.R.style.TextAppearance_Material_Body2)
                setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0) // 8dp top padding
                gravity = Gravity.CENTER_HORIZONTAL
            }
            addView(progressText)
        }

        // Create the root FrameLayout
        return FrameLayout(context).apply {
            addView(recyclerView)
            addView(progressContainer) // Add the container instead of just the ProgressBar
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu()
        setupRecyclerView()
        observeViewModel()

        // Trigger the initial data load.
        viewModel.loadWorkouts()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data on resume, e.g., if details were changed in another activity.
        // The ViewModel can internally prevent redundant loads if desired.
        viewModel.loadWorkouts()
    }


    private fun setupMenu() {
        // Add the MenuProvider to the Fragment's Lifecycle
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Inflate the menu resource file
                menuInflater.inflate(R.menu.workout_summaries_list_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.menu_delete_old_workouts -> {
                        viewModel.onDeleteOldWorkoutsClicked()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupRecyclerView() {
        val isPlayAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireActivity()) == ConnectionResult.SUCCESS

        // Initialize the new adapter.
        workoutAdapter = WorkoutSummariesAdapter(
            requireActivity(),
            parentFragmentManager, // Correct fragment manager for dialogs
            viewLifecycleOwner,
            isPlayAvailable,
            viewModel
        )

        // Set the adapter on the RecyclerView.
        recyclerView.adapter = workoutAdapter
    }

    private fun observeViewModel() {
        // Observe the 'workouts' LiveData. The lambda is executed whenever the data changes.
        // `viewLifecycleOwner` ensures the observer is active only when the view is.
        viewModel.workouts.observe(viewLifecycleOwner) { workoutSummaries ->
            // The ListAdapter will efficiently calculate differences and update the UI.
            workoutAdapter.submitList(workoutSummaries)
        }

        // Observe the delete command
        viewModel.confirmDeleteWorkoutEvent.observe(viewLifecycleOwner) { workoutId ->
            showDeleteConfirmationDialog(workoutId)
        }

        viewModel.showDeleteOldWorkoutsDialogEvent.observe(viewLifecycleOwner) {
            showDeleteOldWorkoutsDialog()
        }

        viewModel.deletionProgress.observe(viewLifecycleOwner) { state ->
            when (state) {
                is DeletionProgress.Idle -> {
                    progressContainer.visibility = View.GONE
                    recyclerView.alpha = 1.0f
                }
                is DeletionProgress.InProgress -> {
                    progressContainer.visibility = View.VISIBLE
                    progressText.text = getString(R.string.deleting_workout, state.workoutName)
                    recyclerView.alpha = 0.5f // Keep the list dimmed
                }
            }
        }

    }

    private fun showDeleteConfirmationDialog(workoutId: Long) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_workout)
            .setMessage(R.string.really_delete_workout)
            .setIcon(android.R.drawable.ic_menu_delete)
            .setPositiveButton(R.string.delete_workout) { _, _ ->
                // If user clicks "Delete", tell the ViewModel to proceed with the deletion.
                viewModel.deleteWorkout(workoutId)
            }
            .setNegativeButton(R.string.cancel, null) // Do nothing on cancel
            .show()
    }

    private fun showDeleteOldWorkoutsDialog() {
        val context = requireContext()

        // Create an EditText for the user to input the number of days.
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(R.string.defaultDaysToKeep)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }

        // We need a container to add some padding around the EditText.
        val container = FrameLayout(context).apply {
            val padding = (20 * resources.displayMetrics.density).toInt() // 20dp
            setPadding(padding, 0, padding, 0)
            addView(input)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.deleteOldWorkouts)
            .setMessage(R.string.deleteWorkoutsThatAreOlderThanDays)
            .setView(container) // Set the container with the EditText
            .setPositiveButton(R.string.OK) { _, _ ->
                // When the user clicks OK, parse the input and call the ViewModel.
                val daysToKeep = input.text.toString().toIntOrNull()
                if (daysToKeep != null) {
                    viewModel.executeDeleteOldWorkouts(daysToKeep)
                }
            }
            .setNegativeButton(R.string.Cancel, null) // Do nothing on cancel
            .show()
    }


    companion object {
        @JvmField
        var TAG: String = "WorkoutSummariesListFragment"
    }
}