package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager
import com.atrainingtracker.trainingtracker.fragments.mapFragments.TrackOnMapTrackingAndFollowingFragment
import com.atrainingtracker.trainingtracker.ui.theme.aTrainingTrackerTheme // Import your Compose theme
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.ConfigureFilterDialog
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldDialog
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldViewModel
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldViewModelFactory

class TrackingFragment : Fragment() {

    private lateinit var viewModel: TrackingViewModel

    private var mapFragment: TrackOnMapTrackingAndFollowingFragment? = null

    private var showMap: Boolean = false
    private var showLapButton: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the viewId from the fragment's arguments
        val tabViewId = arguments?.getLong(ARG_TAB_VIEW_ID) ?: 0
        showMap = arguments?.getBoolean(ARG_SHOW_MAP) ?: false
        showLapButton = arguments?.getBoolean(ARG_SHOW_LAP_BUTTON) ?: false

        // Create the ViewModel using our custom factory
        val factory = TrackingViewModelFactory(requireActivity().application, tabViewId)
        viewModel = ViewModelProvider(this, factory)[TrackingViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Create a ComposeView and set its content
        return ComposeView(requireContext()).apply {
            setContent {
                aTrainingTrackerTheme {
                    val uiState by viewModel.uiState.collectAsState()
                    val activityType by viewModel.activityType.collectAsState()

                    // States to hold the ID of the field and filter to edit. Null means no dialog.
                    var editingSensorFieldId: Long? by remember { mutableStateOf(null) }


                    // -- Main Tracking Screen
                    TrackingScreen(
                        state = uiState,
                        showMap = showMap,
                        showLapButton = showLapButton,
                        onFieldLongClick = { fieldState ->
                            // 2. When a field is long-clicked, just update the state.
                            editingSensorFieldId = fieldState.sensorFieldId
                        },
                        onLapButtonClick = {
                            requireActivity().sendBroadcast(
                                Intent(TrainingApplication.REQUEST_NEW_LAP)
                                    .setPackage(requireActivity().packageName)
                            )
                        },
                        // We are now passing the AndroidView composable INTO the TrackingScreen.
                        mapContent = {
                            if (showMap) { // Double-check just in case
                                AndroidView(
                                    factory = { context ->
                                        val frameLayout = FrameLayout(context).apply { id = View.generateViewId() }
                                        if (childFragmentManager.findFragmentById(frameLayout.id) == null) {
                                            mapFragment = TrackOnMapTrackingAndFollowingFragment.newInstance()
                                            childFragmentManager.beginTransaction()
                                                .add(frameLayout.id, mapFragment!!)
                                                .commit()
                                        }
                                        frameLayout
                                    },
                                    // The modifier here should fill the space provided by the parent Box in TrackingScreen.
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    )

                    // -- Edit Sensor Field Dialog
                    editingSensorFieldId?.let { currentId ->
                        activityType?.let { currentActivityType ->
                            val editViewModel: EditSensorFieldViewModel = viewModel(
                                key = "edit_dialog_$currentId",
                                factory = EditSensorFieldViewModelFactory(
                                    application = requireActivity().application,
                                    sensorFieldId = currentId,
                                    activityType = currentActivityType,
                                    repository = viewModel.trackingRepository
                                )
                            )

                            var showConfigureFilterDialogForId: Long? by remember { mutableStateOf(null) }

                            EditSensorFieldDialog(
                                viewModel = editViewModel,
                                onDismissRequest = {
                                    editViewModel.loadInitialState()
                                    editingSensorFieldId = null },
                                onConfigureFilter = {
                                    // 2. Instead of showing the old fragment, just set the state
                                    showConfigureFilterDialogForId = currentId
                                    // Do not dismiss the edit dialog
                                    // editingSensorFieldId = null
                                }
                            )

                            // -- Configure Filter Dialog
                            showConfigureFilterDialogForId?.let { currentId ->
                                ConfigureFilterDialog(
                                    viewModel = editViewModel,
                                    onDismissRequest = {
                                        editViewModel.onFilterEditCancel()
                                        showConfigureFilterDialogForId = null },
                                    onSave = {
                                        showConfigureFilterDialogForId = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val ARG_TAB_VIEW_ID = "tab_view_id"
        private const val ARG_SHOW_MAP = "show_map"
        private const val ARG_SHOW_LAP_BUTTON = "show_lap_button"

        /**
         * A factory method to create a new instance of this fragment
         * with the required viewId.
         */
        @JvmStatic
        fun newInstance(tabViewId: Long, showMap: Boolean, showLapButton: Boolean) =
            TrackingFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TAB_VIEW_ID, tabViewId)
                    putBoolean(ARG_SHOW_MAP, showMap)
                    putBoolean(ARG_SHOW_LAP_BUTTON, showLapButton)
                }
            }
    }
}