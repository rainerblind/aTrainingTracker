/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.LapEvent
import com.atrainingtracker.trainingtracker.ui.tracking.LapSummaryDialog
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewInfo
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingFragment
import kotlin.properties.Delegates

class TrackingTabsFragment : Fragment() {

    private var isExplicitMode by Delegates.notNull<Boolean>()  // ActivityType is explicitly selected by the user.
    // In this case, we do directly start in edit mode and do not show the control tracking fragment as the first tab

    private lateinit var viewModel: TrackingTabsViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var pagerAdapter: TrackingPagerAdapter
    private lateinit var lapButton: Button

    private var showLapDialog by mutableStateOf(false)
    private var currentLapEvent by mutableStateOf<LapEvent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DEBUG) Log.i(TAG, "onCreate")

        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_tracking_tabs, menu) // Use a menu that has the toggle
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_configure -> {
                viewModel.toggleScreenMode()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (DEBUG) Log.i(TAG, "onCreateView")

        return inflater.inflate(R.layout.fragment_tabbed_container, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (DEBUG) Log.i(TAG, "onViewCreated")

        val factory = TrackingTabsViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(this, factory).get(TrackingTabsViewModel::class.java)

        // Retrieve the type passed from the Activity's Selection Dialog
        val activityTypeName = arguments?.getString(ARG_ACTIVITY_TYPE)
        isExplicitMode = activityTypeName != null

        if (isExplicitMode) {
            val selectedActivityType = ActivityType.valueOf(activityTypeName!!)

            // Lock the ViewModel to the selected sport
            viewModel.setExplicitActivityType(selectedActivityType)

            // Force the UI into Configuration Mode immediately
            // Thereby, we check if already in config to avoid toggling back and forth
            if (viewModel.screenMode.value != ScreenMode.CONFIGURATION) {
                viewModel.toggleScreenMode()
            }
        }
        else {
            // ViewModel will follow BANALService/Repository activity type
            // No action needed here; the ViewModel should handle its own default flow
        }

        viewPager = view.findViewById(R.id.pager)
        tabLayout = view.findViewById(R.id.tab_layout)
        val configHeader = view.findViewById<ComposeView>(R.id.tab_config_header)

        lapButton = view.findViewById(R.id.fab_lap_button)
        lapButton.setOnClickListener {
            // simply inform the view model that the button was clicked.
            viewModel.onLapButtonClick()
        }

        val composeView = view.findViewById<ComposeView>(R.id.compose_view_dialog_host)
        composeView.setContent {
            ATrainingTrackerTheme {
                if (showLapDialog) {
                    val event = currentLapEvent
                    if (event != null) {
                        LapSummaryDialog(
                            lapNr = event.lapNumber,
                            lapTime = event.lapTime,
                            lapDistance = event.lapDistance,
                            lapSpeed = event.lapSpeed,
                            onDismissRequest = {
                                // Hide the dialog and clear the event
                                showLapDialog = false
                                currentLapEvent = null
                            }
                        )
                    }
                }
            }
        }

        // Initialize the adapter
        pagerAdapter = TrackingPagerAdapter(this, showControlTab = !isExplicitMode)
        viewPager.adapter = pagerAdapter

        // Add a page change callback to update the header and the lap buttons visibility
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                updateConfigHeader(configHeader)
                updateLapButtonVisibility()
            }
        })


        // --- BACK BUTTON HANDLING ---
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // If we are in Configuration mode, just exit the mode instead of closing the app
                if (viewModel.screenMode.value == ScreenMode.CONFIGURATION) {
                    viewModel.toggleScreenMode()
                    requireActivity().invalidateOptionsMenu()
                } else {
                    // If we are in Tracking mode, disable this callback and let the activity handle it (close app/go back)
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activityType.collect { activityType ->
                    if (DEBUG) Log.i(TAG, "ActivityType updated: $activityType")
                    attachTabLayoutMediator()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                viewModel.navigationEvent.collect { tabNavigationEvent ->
                    when (tabNavigationEvent) {
                        is TabNavigationEvent.NavigateTo -> {
                            // If control tab is hidden in explicitMode, target is exactly the index; otherwise, the offset is 1
                            val offset = if (isExplicitMode) 0 else 1
                            val target = tabNavigationEvent.index + offset
                            viewPager.setCurrentItem(target, true)
                        }
                    }
                }
            }
        }

        viewModel.navigateToTrackingTab.observe(viewLifecycleOwner) {
            // Switch to the second tab (index 1)
            viewPager.setCurrentItem(1, false)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.trackingViews.collect { trackingViews ->
                    if (::pagerAdapter.isInitialized) {
                        pagerAdapter.updateTrackingViews(trackingViews)

                        if (::tabLayout.isInitialized) {
                            for (i in 0 until tabLayout.tabCount) {
                                tabLayout.getTabAt(i)?.text = pagerAdapter.getPageTitle(i)
                            }
                        }
                        updateLapButtonVisibility()
                        updateConfigHeader(configHeader)
                    }
                }
            }
        }

        // Observe TrackingMode to update the tab text of the first tab (the control tracking fragment)
        viewModel.trackingMode.observe(viewLifecycleOwner) { trackingMode ->
            if (isExplicitMode) return@observe // when in explicit mode, there is no control tracking fragment, so we immediately return.

            // set the title
            if (::tabLayout.isInitialized && ::pagerAdapter.isInitialized) {
                tabLayout.getTabAt(0)?.text = pagerAdapter.getPageTitle(0)
            }

            // set enabled when tracking / disabled else
            if (trackingMode == TrackingMode.TRACKING) {
                lapButton.isEnabled = true
                lapButton.alpha = 1.0f
                // set to primary brand color
                lapButton.setBackgroundColor(requireContext().getColor(R.color.color_primary))
                lapButton.setTextColor(requireContext().getColor(R.color.color_on_primary))
            }
            else {
                lapButton.isEnabled = false
                // Make it look "ghosted" or disabled
                lapButton.alpha = 0.5f
                // Use a neutral/disabled grey
                lapButton.setBackgroundColor(requireContext().getColor(R.color.lap_button_disabled_background))
                lapButton.setTextColor(requireContext().getColor(R.color.lap_button_disabled_text))
            }

        }

        // Observe ScreenMode to swap between Tracking and Configuration UI
        lifecycleScope.launch {
            viewModel.screenMode.collect { mode ->
                // Force the menu to update (shows/hides toggle icon)
                requireActivity().invalidateOptionsMenu()

                // Refresh the Compose content whenever mode or selection changes
                updateConfigHeader(configHeader)
            }
        }

        viewModel.lapEvent.observe(viewLifecycleOwner) { lapEvent ->
            // Check that the control tab isn't active and that we have a valid event
            if (viewPager.currentItem != 0 && lapEvent != null) {
                currentLapEvent = lapEvent
                showLapDialog = true
            }
        }

        Log.i(TAG, "End of onViewCreated")
    }

    private fun updateConfigHeader(composeView: ComposeView) {
        if (!::pagerAdapter.isInitialized || !::viewPager.isInitialized) {
            composeView.visibility = View.GONE
            return
        }

        val position = viewPager.currentItem

        val viewInfo = pagerAdapter.getTrackingViewInfo(position)

        // If NOT in explicit mode, position 0 is the Control tab (Hide header)
        // If IN explicit mode, position 0 is a sensor tab (Show header)
        val isControlTab = !isExplicitMode && position == 0

        if (viewModel.screenMode.value != ScreenMode.CONFIGURATION || isControlTab || viewInfo == null) {
            composeView.visibility = View.GONE
            return
        }

        composeView.visibility = View.VISIBLE
        composeView.setContent {
            ATrainingTrackerTheme {
                // Call the actual Composable
                TabConfigContent(viewInfo)
            }
        }
    }

    @Composable
    private fun TabConfigContent(viewInfo: TrackingViewInfo) {
        var localName by remember(viewInfo.tabViewId) { mutableStateOf(viewInfo.name) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(8.dp)
        ) {
            // Row 1: Tab Name Input
            OutlinedTextField(
                value = localName,
                onValueChange = {
                    localName = it
                    viewModel.onUpdateTabName(viewInfo.tabViewId, it)
                },
                label = { Text(stringResource(R.string.tab_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(4.dp))

            // Row 2: Management Buttons (Add Before, Delete, Add After)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Add Before
                IconButton(onClick = { viewModel.onAddTabRelative(viewInfo.tabViewId, false) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Before", tint = MaterialTheme.colorScheme.primary)
                }

                // Delete in the middle
                IconButton(onClick = { viewModel.onDeleteTab(viewInfo.tabViewId) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Tab"
                    )
                }

                // Add After
                IconButton(onClick = { viewModel.onAddTabRelative(viewInfo.tabViewId, true) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add After", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(4.dp))

            // Row 3: Settings (The three Checkboxes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConfigCheckbox(
                    label = stringResource(R.string.showMap),
                    checked = viewInfo.showMap,
                    onCheckedChange = { viewModel.onUpdateShowMap(viewInfo.tabViewId, it) }
                )
                ConfigCheckbox(
                    label = stringResource(R.string.showLiveSegments),
                    checked = viewInfo.showLiveSegments,
                    onCheckedChange = { viewModel.onUpdateShowLiveSegments(viewInfo.tabViewId, it) }
                )
                ConfigCheckbox(
                    label = stringResource(R.string.showLapButton),
                    checked = viewInfo.showLapButton,
                    onCheckedChange = { viewModel.onUpdateShowLapButton(viewInfo.tabViewId, it) }
                )
            }

        }
    }

    /**
     * Helper to keep the Checkbox logic clean
     */
    @Composable
    private fun ConfigCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }

    private fun attachTabLayoutMediator() {
        // SAFETY CHECK: Prevent crash if adapter isn't ready yet
        if (!::tabLayout.isInitialized || !::viewPager.isInitialized ||viewPager.adapter == null) {
            return
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            // Standard Text Tab
            tab.text = pagerAdapter.getPageTitle(position)
            tab.customView = null
        }.attach()
    }

    private fun updateLapButtonVisibility() {
        if (!isAdded || !::pagerAdapter.isInitialized) {
            lapButton.visibility = View.GONE
            return
        }

        val currentPosition = viewPager.currentItem

        if (currentPosition == 0) {
            // Never show for the "Control" tab
            lapButton.visibility = View.GONE
        }
        else {
            if (pagerAdapter.getTrackingViewInfo(currentPosition)?.showLapButton == true) {
                lapButton.visibility = View.VISIBLE
            } else {
                lapButton.visibility = View.GONE
            }
        }
    }

    private class TrackingPagerAdapter(
        private val fragment: TrackingTabsFragment,
        private val showControlTab: Boolean
    ) : FragmentStateAdapter(fragment) {

        private var trackingViews: List<TrackingViewInfo> = emptyList()
        private val viewModel: TrackingTabsViewModel by lazy {
            ViewModelProvider(fragment).get(TrackingTabsViewModel::class.java)
        }

        fun updateTrackingViews(newViews: List<TrackingViewInfo>) {
            Log.i(TAG, "updateTrackingViews")
            this.trackingViews = newViews

            notifyDataSetChanged()

            // Trigger the fragment to re-attach the mediator to show the new tabs
            fragment.attachTabLayoutMediator()
            if (DEBUG) Log.i(TAG, "updateTrackingViews: $trackingViews")
        }

        fun getPageTitle(position: Int): CharSequence {
            return if (showControlTab && position == 0) {
                when (viewModel.trackingMode.value) {
                    TrackingMode.PAUSED -> fragment.getString(R.string.Paused)
                    TrackingMode.TRACKING -> fragment.getString(R.string.Tracking)
                    else -> fragment.getString(R.string.tab_start)
                }
            } else {
                val viewIndex = if (showControlTab) position - 1 else position
                trackingViews[viewIndex].name
            }
        }

        fun getTrackingViewInfo(position: Int): TrackingViewInfo? {
            val viewIndex = if (showControlTab) position - 1 else position
            return if (viewIndex >= 0 && viewIndex < trackingViews.size) {
                trackingViews[viewIndex]
            } else null
        }

        override fun getItemCount(): Int {
            val baseCount = if (showControlTab) 1 else 0
            return baseCount + trackingViews.size
        }

        override fun createFragment(position: Int): Fragment {
            Log.i(TAG, "createFragment, pos=$position")

            return if (showControlTab && position == 0) {
                ControlTrackingFragment()
            } else {
                // If control tab is hidden, position 0 is trackingViews[0]
                // If control tab is shown, position 1 is trackingViews[0]
                val viewIndex = if (showControlTab) position - 1 else position
                val viewInfo = trackingViews[viewIndex]
                TrackingFragment.newInstance(viewInfo.tabViewId)
            }
        }

        override fun getItemId(position: Int): Long {
            if (DEBUG) Log.i(TAG, "getItemId, pos=$position")

            if (showControlTab && position == 0) { return -1L}

            val viewIndex = if (showControlTab) position - 1 else position
            return if (viewIndex >= 0 && viewIndex < trackingViews.size) {
                trackingViews[viewIndex].tabViewId
            } else {
                RecyclerView.NO_ID
            }
        }

        override fun containsItem(itemId: Long): Boolean {
            if (DEBUG) Log.i(TAG, "containsItem, itemId=$itemId")

            if (showControlTab && itemId == -1L) return true
            return trackingViews.any { it.tabViewId == itemId }
        }

    }


    companion object {
        val DEBUG = TrainingApplication.getDebug(true)
        @JvmField
        val TAG = "TrackingTabsFragment"
        const val ARG_ACTIVITY_TYPE = "arg_activity_type"

        @JvmStatic
        fun newInstance(activityType: ActivityType): TrackingTabsFragment {
            return TrackingTabsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ACTIVITY_TYPE, activityType.name)
                }
            }
        }

        @JvmStatic
        fun newInstance(): TrackingTabsFragment {
            return TrackingTabsFragment()
        }
    }
}

