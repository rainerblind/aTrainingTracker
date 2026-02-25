package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.atrainingtracker.trainingtracker.ui.theme.aTrainingTrackerTheme // Import your Compose theme

class TrackingFragment : Fragment() {

    private lateinit var viewModel: TrackingViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the viewId from the fragment's arguments
        val viewId = arguments?.getInt(ARG_VIEW_ID) ?: 0

        // Create the ViewModel using our custom factory
        val factory = TrackingViewModelFactory(requireActivity().application, viewId)
        viewModel = ViewModelProvider(this, factory)[TrackingViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Create a ComposeView and set its content
        return ComposeView(requireContext()).apply {
            setContent {
                // Apply your MaterialTheme
                aTrainingTrackerTheme {
                    // Collect the state from the ViewModel as a Compose State
                    val uiState by viewModel.uiState.collectAsState()

                    // Pass the state to your main screen Composable
                    // (We will create TrackingScreen Composable next)
                    TrackingScreen(state = uiState)
                }
            }
        }
    }

    companion object {
        private const val ARG_VIEW_ID = "view_id"

        /**
         * A factory method to create a new instance of this fragment
         * with the required viewId.
         */
        @JvmStatic
        fun newInstance(viewId: Int) =
            TrackingFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_VIEW_ID, viewId)
                }
            }
    }
}