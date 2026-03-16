package com.atrainingtracker.banalservice.ui.sporttype

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesListFragment
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

class SportTypeListFragment : Fragment() {

    private val viewModel: SportTypeViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    // The Screen now handles its own state for adding/editing/deleting
                    SportTypeScreen(
                        viewModel = viewModel,
                        onNavigateToWorkouts = { stats ->
                            // When a stats block is clicked, navigate to the filtered workout list
                            navigateToFilteredWorkouts(stats)
                        }
                    )
                }
            }
        }
    }

    /**
     * Navigates to the WorkoutSummariesListFragment with the filters
     * defined in the clicked StatsData.
     */
    private fun navigateToFilteredWorkouts(stats: com.atrainingtracker.trainingtracker.ui.components.stats.StatsData) {
        val fragment = WorkoutSummariesListFragment.newInstance(
            sportTypeId = stats.filterSportTypeId,
            equipmentId = stats.filterEquipmentId,
            startS = stats.startTimeS,
            endS = stats.endTimeS
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.content, fragment) // Ensure this ID matches your Activity's container
            .addToBackStack(null)
            .commit()
    }

    companion object {
        val TAG: String = SportTypeListFragment::class.java.name

        @JvmStatic
        fun newInstance(): SportTypeListFragment {
            return SportTypeListFragment()
        }
    }
}