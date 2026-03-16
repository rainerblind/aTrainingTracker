package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.atrainingtracker.banalservice.BSportType
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

class WorkoutListChildFragment : Fragment() {
    // Access the ViewModel of the Parent Fragment
    private val viewModel: WorkoutSummariesViewModel by activityViewModels()
    private lateinit var workoutAdapter: WorkoutSummariesAdapter
    private lateinit var recyclerView: RecyclerView

    companion object {
        const val ARG_BSPORT_TYPE = "ARG_BSPORT_TYPE"
        const val ARG_SPORT_ID = "ARG_SPORT_ID"
        const val ARG_EQUIP_ID = "ARG_EQUIP_ID"
        const val ARG_START_S = "ARG_START_S"
        const val ARG_END_S = "ARG_END_S"

        fun newInstance(
            bSportType: BSportType? = null,
            sportTypeId: Long? = null,
            equipmentId: Long? = null,
            startS: Long? = null,
            endS: Long? = null
        ) = WorkoutListChildFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_BSPORT_TYPE, bSportType)
                sportTypeId?.let { putLong(ARG_SPORT_ID, it) }
                equipmentId?.let { putLong(ARG_EQUIP_ID, it) }
                startS?.let { putLong(ARG_START_S, it) }
                endS?.let { putLong(ARG_END_S, it) }
            }
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

        // Retrieve arguments using constants
        val bSportType = arguments?.getSerializable(ARG_BSPORT_TYPE) as? BSportType
        val sportId = arguments?.getLong(ARG_SPORT_ID, -1)?.takeIf { it != -1L }
        val equipId = arguments?.getLong(ARG_EQUIP_ID, -1)?.takeIf { it != -1L }
        val startS = arguments?.getLong(ARG_START_S, -1L)?.takeIf { it != -1L }
        val endS = arguments?.getLong(ARG_END_S, -1L)?.takeIf { it != -1L }

        // Observe the filtered list
        viewModel.getFilteredWorkouts(
            bSportType = bSportType,
            sportTypeId = sportId,
            equipmentId = equipId,
            startTimeS = startS,
            endTimeS = endS
        ).observe(viewLifecycleOwner) { workouts ->
            workoutAdapter.submitList(workouts)
        }
    }
}