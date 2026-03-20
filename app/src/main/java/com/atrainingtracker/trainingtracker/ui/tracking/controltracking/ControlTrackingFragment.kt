package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.BANALServiceRepository

class ControlTrackingFragment : Fragment() {

    companion object {
        private const val DEBUG = true
        private const val TAG = "ControlTrackingFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (DEBUG) Log.i(TAG, "onCreateView")

        return ComposeView(requireContext()).apply {
            setContent {
                // This correctly creates the ViewModel with its required dependencies
                val viewModel: ControlTrackingViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ControlTrackingViewModel(
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
                val trackingMode by viewModel.trackingMode.observeAsState(TrackingMode.READY)

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
                Log.i(TAG, "c")

                // Handle Navigation directly in the Fragment
                LaunchedEffect(Unit) {
                    viewModel.navigationEvent.collect { protocol ->
                        (activity as? MainActivityWithNavigation)?.startPairing(protocol)
                        /*
                        // 1. Create the new fragment instance
                        val pairingFragment = DevicesTabbedContainerFragment.newInstance(protocol)

                        requireActivity().supportFragmentManager.beginTransaction()
                            .replace(R.id.content, pairingFragment)
                            .addToBackStack(null) // This ensures the "Back" button returns you to the Tabs
                            .commit()
                         */
                    }
                }
            }
        }
    }
}