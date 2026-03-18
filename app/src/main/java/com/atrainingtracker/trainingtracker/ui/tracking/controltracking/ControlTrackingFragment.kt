package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingRepository

class ControlTrackingFragment : Fragment() {

    // You likely have a way to get your repository, e.g., from your App class
    // or by instantiating it here if it doesn't exist yet.
    private lateinit var repository: TrackingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize your repository here.
        // Example: repository = (requireActivity().application as YourAppClass).repository
        repository = TrackingRepository.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                // This correctly creates the ViewModel with its required dependencies
                val viewModel: TrackingViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return TrackingViewModel(
                                repository,
                                requireContext().applicationContext
                            ) as T
                        }
                    }
                )

                // Apply your Material Theme wrapper here if you have one
                androidx.compose.material3.MaterialTheme {
                    // Your new Compose Screen
                    ControlTrackingScreen(viewModel)
                }
            }
        }
    }
}