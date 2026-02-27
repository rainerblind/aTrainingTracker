package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager
import com.atrainingtracker.trainingtracker.ui.theme.aTrainingTrackerTheme // Import your Compose theme
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.ConfigureFilterDialog
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldDialog
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldViewModel
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldViewModelFactory

class TrackingFragment : Fragment() {

    private lateinit var viewModel: TrackingViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the viewId from the fragment's arguments
        val tabViewId = arguments?.getLong(ARG_TAB_VIEW_ID) ?: 0

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
                        onFieldLongClick = { fieldState ->
                            // 2. When a field is long-clicked, just update the state.
                            editingSensorFieldId = fieldState.sensorFieldId
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
                                onDismissRequest = { editingSensorFieldId = null },
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

        /**
         * A factory method to create a new instance of this fragment
         * with the required viewId.
         */
        @JvmStatic
        fun newInstance(tabViewId: Long) =
            TrackingFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TAB_VIEW_ID, tabViewId)
                }
            }
    }
}