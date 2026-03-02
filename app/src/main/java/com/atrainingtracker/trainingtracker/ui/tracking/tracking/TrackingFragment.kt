package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.os.Bundle
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
import androidx.compose.runtime.remember
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
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.ConfigureFilterDialog
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldDialog
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldViewModel
import com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldViewModelFactory

class TrackingFragment : Fragment() {

    private lateinit var viewModel: TrackingViewModel

    private var mapFragment: TrackOnMapTrackingAndFollowingFragment? = null

    private var tabViewId: Long = -1L
    private var showMap: Boolean = false

    private var screenMode by mutableStateOf(ScreenMode.TRACKING)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        // Get the viewId from the fragment's arguments
        arguments?.let {
            tabViewId = it.getLong(ARG_TAB_VIEW_ID) ?: 0
            showMap = it.getBoolean(ARG_SHOW_MAP) ?: false
        }

        // Create the ViewModel using our custom factory
        val factory = TrackingViewModelFactory(requireActivity().application, tabViewId)
        viewModel = ViewModelProvider(this, factory)[TrackingViewModel::class.java]
    }

    // Inflate the menu
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_tracking_fragment, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    // Handle menu item clicks
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_configure -> {
                // Toggle the screen mode
                screenMode = if (screenMode == ScreenMode.TRACKING) {
                    ScreenMode.CONFIGURATION
                } else {
                    ScreenMode.TRACKING
                }
                true // Consume the event
            }
            else -> super.onOptionsItemSelected(item)
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

                    // States to hold the ID of the field and filter to edit. Null means no dialog.
                    var editingSensorFieldId: Long? by remember { mutableStateOf(null) }

                    // Create the GridActions object
                    val gridActions = object : GridActions {
                        override fun onEditField(fieldState: SensorFieldState) {
                            // TODO: Call viewModel to show edit dialog
                            // viewModel.onEditField(fieldState)
                        }
                        override fun onDeleteField(fieldState: SensorFieldState) {
                            // TODO: Call viewModel to delete field
                            // viewModel.onDeleteField(fieldState)
                        }
                        override fun onAddRow(atRow: Int) {
                            // TODO: Call viewModel to add a row
                            // viewModel.onAddRow(atRow)
                        }
                        override fun onAddCol(atRow: Int, atCol: Int) {
                            // TODO: Call viewModel to add a column
                            // viewModel.onAddCol(atRow, atCol)
                        }
                    }

                    // 6. Pass the current screenMode to the SensorGridScreen
                    SensorGridScreen(
                        state = uiState,
                        screenMode = screenMode,
                        gridActions = gridActions, // Pass the actions object
                        showMap = showMap,
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
                }
            }
        }
    }

    companion object {
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