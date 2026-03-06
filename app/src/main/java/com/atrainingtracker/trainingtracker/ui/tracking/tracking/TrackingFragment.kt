package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.fragments.mapFragments.TrackOnMapTrackingAndFollowingFragment
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldDialog
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldViewModelFactory

class TrackingFragment : Fragment() {

    private lateinit var viewModel: TrackingViewModel

    private var mapFragment: TrackOnMapTrackingAndFollowingFragment? = null

    private var tabViewId: Long = -1L

    private var showMapState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the viewId from the fragment's arguments
        arguments?.let {
            tabViewId = it.getLong(ARG_TAB_VIEW_ID) ?: 0
            showMapState = it.getBoolean(ARG_SHOW_MAP) ?: false
        }

        // Create the ViewModel using our custom factory
        val factory = TrackingViewModelFactory(requireActivity().application, tabViewId)
        viewModel = ViewModelProvider(this, factory)[TrackingViewModel::class.java]
    }

    fun updateShowMap(show: Boolean) {
        if (showMapState != show) {
            showMapState = show
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Create a ComposeView and set its content
        return ComposeView(requireContext()).apply {
            setContent {
                ATrainingTrackerTheme {
                    val uiState by viewModel.uiState.collectAsState()
                    val activityType by viewModel.activityType.collectAsState()

                    val screenMode by viewModel.screenMode.collectAsState()

                    val editingFieldId by viewModel.editingFieldId.collectAsState()
                    val pendingAddition by viewModel.pendingAddition.collectAsState()

                    // If editingFieldId is not null, the Dialog is added to the UI composition.
                    editingFieldId?.let { fieldId ->
                        EditSensorFieldDialog(
                            title = context.getString(R.string.edit_field),
                            viewModel = viewModel(
                                factory = EditSensorFieldViewModelFactory(
                                    application = requireActivity().application,
                                    sensorFieldId = fieldId,
                                    activityType = activityType,
                                    repository = viewModel.trackingRepository,
                                    tabViewId = tabViewId,
                                    rowNr = -1,
                                    colNr = -1
                                ),
                                // Crucial: Use the fieldId as a key so a new ViewModel
                                // is created if you switch from editing field A to field B
                                key = fieldId.toString()
                            ),
                            onDismissRequest = {
                                // Tell the TrackingViewModel to reset the state to null
                                viewModel.onDismissEditDialog()
                            }
                        )
                    }

                    // Show the EditSensorFieldDialog to add a new sensor field
                    pendingAddition?.let { params ->
                        Log.i(TAG, "pendingAddition: $params ($tabViewId)")
                        EditSensorFieldDialog(
                            title = context.getString(R.string.add_sensor),
                            viewModel = viewModel(
                                factory = EditSensorFieldViewModelFactory(
                                    application = requireActivity().application,
                                    repository = viewModel.trackingRepository,
                                    activityType = activityType,
                                    sensorFieldId = -1L, // Signal NEW mode
                                    tabViewId = tabViewId,
                                    rowNr = params.row,
                                    colNr = params.col,
                                ),
                                key = "$tabViewId, ${params.row}, ${params.col}"
                            ),
                            onDismissRequest = { viewModel.onDismissAddition() }
                        )
                    }

                    // Create the GridActions object
                    val gridActions = object : GridActions {
                        override fun onEditField(fieldState: SensorFieldState) {
                            // simply forward this to the viewModel to show edit dialog
                            viewModel.onEditField(fieldState)
                        }
                        override fun onDeleteField(fieldState: SensorFieldState) {
                            viewModel.onDeleteSensorField(fieldState.sensorFieldId)
                        }
                        override fun onAddRow(atRow: Int) {
                            Log.i(TAG, "onAddRow($atRow)")
                            viewModel.onAddRow(atRow)
                        }
                        override fun onAddCol(atRow: Int, atCol: Int) {
                            Log.i(TAG, "onAddCol($atRow, $atCol)")
                            viewModel.onAddCol(atRow, atCol)
                        }
                    }

                    // 6. Pass the current screenMode to the SensorGridScreen
                    SensorGridScreen(
                        state = uiState,
                        screenMode = screenMode,
                        gridActions = gridActions, // Pass the actions object
                        showMap = showMapState,
                        mapContent = {
                            if (showMapState) { // Double-check just in case
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
                }
            }
        }
    }

    companion object {
        private const val TAG = "TrackingFragment"
        private const val ARG_TAB_VIEW_ID = "tab_view_id"
        private const val ARG_SHOW_MAP = "show_map"

        /**
         * A factory method to create a new instance of this fragment
         * with the required viewId.
         */
        @JvmStatic
        fun newInstance(tabViewId: Long, showMap: Boolean) =
            TrackingFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TAB_VIEW_ID, tabViewId)
                    putBoolean(ARG_SHOW_MAP, showMap)
                }
            }
    }
}