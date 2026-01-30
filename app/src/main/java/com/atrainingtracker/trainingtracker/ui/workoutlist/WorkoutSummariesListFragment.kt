package com.atrainingtracker.trainingtracker.ui.workoutlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

        setupRecyclerView()
        observeViewModel()

        // Trigger the initial data load.
        viewModel.loadWorkouts()
    }

    private fun setupRecyclerView() {
        val isPlayAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireActivity()) == ConnectionResult.SUCCESS

        // Initialize the new adapter.
        workoutAdapter = WorkoutSummariesAdapter(
            requireActivity(),
            parentFragmentManager, // Correct fragment manager for dialogs
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

            // You could handle an empty view's visibility here.
            // emptyView.visibility = if (workoutSummaries.isNullOrEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data on resume, e.g., if details were changed in another activity.
        // The ViewModel can internally prevent redundant loads if desired.
        viewModel.loadWorkouts()
    }

    companion object {
        @JvmField
        var TAG: String = "WorkoutSummariesListFragment"
    }
}