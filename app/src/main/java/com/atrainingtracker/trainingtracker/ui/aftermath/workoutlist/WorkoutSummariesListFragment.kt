package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.interfaces.ShowWorkoutDetailsInterface
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Programmatically create the RecyclerView, similar to the Java version.
        recyclerView = RecyclerView(requireContext()).apply {
            id = View.generateViewId() // For state restoration
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setHasFixedSize(true) // Important for performance
            layoutManager = LinearLayoutManager(context)
        }
        return recyclerView
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
            // By returning 'false', we tell the system that we have NOT handled the click,
            // so it should continue to pass the event to other components (like the Activity).
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false
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

            // Observe the delete command
            viewModel.confirmDeleteWorkoutEvent.observe(viewLifecycleOwner) { workoutId ->
                showDeleteConfirmationDialog(workoutId)
                // (activity as? ReallyDeleteDialogInterface)?.confirmDeleteWorkout(workoutId)
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


    companion object {
        @JvmField
        var TAG: String = "WorkoutSummariesListFragment"
    }
}