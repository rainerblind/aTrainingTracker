package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.BANALServiceRepository
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository

class ControlTrackingFragment : Fragment() {

    // You likely have a way to get your repository, e.g., from your App class
    // or by instantiating it here if it doesn't exist yet.
    private lateinit var repository: BANALServiceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize your repository here.
        // Example: repository = (requireActivity().application as YourAppClass).repository
        repository = BANALServiceRepository.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                // This correctly creates the ViewModel with its required dependencies
                val viewModel: ControlTrackingViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ControlTrackingViewModel(
                                repository,
                                requireContext().applicationContext
                            ) as T
                        }
                    }
                )

                // Collect StateFlows (using collectAsState)
                val searchingFor by viewModel.searchingForDevice.collectAsState()
                val devices by viewModel.remoteDevices.collectAsState()
                val activeSensors by viewModel.activeSensors.collectAsState()
                val bSportType by viewModel.bSportType.collectAsState()

                // Collect LiveData (using observeAsState)
                val trackingMode by viewModel.trackingMode.observeAsState(TrackingMode.WAITING_FOR_BANAL_SERVICE)

                ATrainingTrackerTheme {
                    Surface {
                        ControlTrackingScreen(
                            trackingMode = trackingMode,
                            searchingFor = searchingFor,
                            devices = devices,
                            activeSensors = activeSensors,
                            currentSport = bSportType,
                            isAntSupported = viewModel.isAntProperlyInstalled(),
                            isBluetoothSupported = viewModel.isBluetoothSupported(),
                            onSearch = { viewModel.onSearchClicked() },
                            onDeviceClick = { viewModel.onDeviceClicked(it) },
                            onSportSelected = { viewModel.setSport(it) },
                            onStart = { viewModel.onStartTracking() },
                            onPause = { viewModel.onPauseTracking() },
                            onResume = { viewModel.onResumeTracking() },
                            onStop = { viewModel.onStopTracking() },
                            onPairingClicked = { viewModel.onPairingClicked(it) }
                        )
                    }
                }
            }
        }
    }
}