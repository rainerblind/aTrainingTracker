package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.atrainingtracker.banalservice.BSportType
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

class WorkoutListChildFragment : Fragment() {
    // Access the ViewModel of the Parent Fragment
    private val viewModel: WorkoutSummariesViewModel by viewModels({ requireParentFragment() })
    private lateinit var workoutAdapter: WorkoutSummariesAdapter
    private lateinit var recyclerView: RecyclerView

    companion object {
        fun newInstance(type: BSportType?) = WorkoutListChildFragment().apply {
            arguments = Bundle().apply { putSerializable("filter_type", type) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            layoutManager = LinearLayoutManager(context)
        }
        return recyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val filterType = arguments?.getSerializable("filter_type") as? BSportType

        val isPlayAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireActivity()) == ConnectionResult.SUCCESS

        // Setup adapter
        workoutAdapter = WorkoutSummariesAdapter(
            requireActivity(),
            parentFragmentManager, // Correct fragment manager for dialogs
            viewLifecycleOwner,
            isPlayAvailable,
            viewModel
        )
        recyclerView.adapter = workoutAdapter

        // Observe the SPECIFIC filtered list for this tab
        viewModel.getWorkoutsForTab(filterType).observe(viewLifecycleOwner) {
            workoutAdapter.submitList(it)
        }
    }
}