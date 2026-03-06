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
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.fragments.ControlTrackingFragment
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
import androidx.viewpager2.adapter.FragmentViewHolder

class TrackingTabsFragment : Fragment() {

    private lateinit var viewModel: TrackingTabsViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var pagerAdapter: TrackingPagerAdapter
    private lateinit var lapButton: Button

    private var showLapDialog by mutableStateOf(false)
    private var currentLapEvent by mutableStateOf<LapEvent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        return inflater.inflate(R.layout.fragment_tabbed_container, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val factory = TrackingTabsViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(this, factory).get(TrackingTabsViewModel::class.java)

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
        pagerAdapter = TrackingPagerAdapter(this)
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

        // Observe the ActivityType from the ViewModel (which gets it from the repository)
        viewModel.activityType.observe(viewLifecycleOwner) { activityType ->
            attachTabLayoutMediator()
        }


        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                viewModel.navigationEvent.collect { tabNavigationEvent ->
                    when (tabNavigationEvent) {
                        is TabNavigationEvent.NavigateTo -> {

                            val target =
                                tabNavigationEvent.index + 1 // Note that we have to add one due to the fact that we also have the control tracking fragment.
                            viewPager.setCurrentItem(target, true)
                        }
                    }
                }
            }
        }

        viewModel.trackingViews.observe(viewLifecycleOwner) { trackingViews ->
            if (::pagerAdapter.isInitialized) {

                // note that we call notifyDatasetChanged already here...
                pagerAdapter.updateTrackingViews(trackingViews)

                // For renames/settings, we don't want to jump pages, just refresh visuals
                if (::tabLayout.isInitialized) {
                    for (i in 0 until tabLayout.tabCount) {
                        tabLayout.getTabAt(i)?.text = pagerAdapter.getPageTitle(i)
                    }
                }
                updateLapButtonVisibility()
                updateConfigHeader(configHeader)
            }
        }

        // Observe TrackingMode to update the tab text of the first tab (the control tracking fragment)
        viewModel.trackingMode.observe(viewLifecycleOwner) { _ ->
            if (::tabLayout.isInitialized && ::pagerAdapter.isInitialized) {
                tabLayout.getTabAt(0)?.text = pagerAdapter.getPageTitle(0)
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
    }

    private fun updateConfigHeader(composeView: ComposeView) {
        if (!::pagerAdapter.isInitialized || !::viewPager.isInitialized) {
            composeView.visibility = View.GONE
            return
        }

        val position = viewPager.currentItem
        val isFirstTab = position == 0
        val viewInfo = pagerAdapter.getTrackingViewInfo(position)

        if (viewModel.screenMode.value != ScreenMode.CONFIGURATION || isFirstTab || viewInfo == null) {
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
        // We use viewInfo.tabViewId as a key so that when you swipe tabs, the local state resets.
        var localName by remember(viewInfo.tabViewId) { mutableStateOf(viewInfo.name) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(8.dp)
        ) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.onAddTabRelative(viewInfo.tabViewId, false) }) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = viewInfo.showMap,
                        onCheckedChange = { viewModel.onUpdateShowMap(viewInfo.tabViewId, it) }
                    )
                    Text(stringResource(R.string.showMap), style = MaterialTheme.typography.labelSmall)

                    Checkbox(
                        checked = viewInfo.showLapButton,
                        onCheckedChange = { viewModel.onUpdateShowLapButton(viewInfo.tabViewId, it) }
                    )
                    Text(stringResource(R.string.showLapButton), style = MaterialTheme.typography.labelSmall)

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = { viewModel.onDeleteTab(viewInfo.tabViewId) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Field", // For accessibility
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { viewModel.onAddTabRelative(viewInfo.tabViewId, true) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add After", tint = MaterialTheme.colorScheme.primary)
                }
            }
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
        private val fragment: TrackingTabsFragment
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
        }

        override fun onBindViewHolder(
            holder: FragmentViewHolder,
            position: Int,
            payloads: MutableList<Any>
        ) {
            if (position > 0) { // Skip the Control tab
                val fragment = fragment.childFragmentManager.findFragmentByTag("f" + holder.itemId) as? TrackingFragment
                val viewInfo = trackingViews[position - 1]

                // Push the new value into the existing fragment
                fragment?.updateShowMap(viewInfo.showMap)
            }
            super.onBindViewHolder(holder, position, payloads)
        }

        fun getPageTitle(position: Int): CharSequence {
            return if (position == 0) {
                when (viewModel.trackingMode.value) {
                    TrackingMode.PAUSED -> fragment.getString(R.string.Paused)
                    TrackingMode.TRACKING -> fragment.getString(R.string.Tracking)
                    else -> fragment.getString(R.string.tab_start) // STOPPED or null
                }
            } else {
                trackingViews[position - 1].name
            }
        }

        fun getTrackingViewInfo(position: Int): TrackingViewInfo? {
            val viewIndex = position - 1
            return if (viewIndex >= 0 && viewIndex < trackingViews.size) {
                trackingViews[viewIndex]
            } else {
                null
            }
        }

        override fun getItemCount(): Int = 1 + trackingViews.size

        override fun createFragment(position: Int): Fragment {
            return if (position == 0) {
                ControlTrackingFragment()
            } else {
                val viewInfo = trackingViews[position - 1]
                TrackingFragment.newInstance(viewInfo.tabViewId, viewInfo.showMap)
            }
        }

        override fun getItemId(position: Int): Long {
            Log.i(TAG, "getItemId: position=$position")

            // Position 0 is the fixed Control tab. Give it a unique, constant ID.
            if (position == 0) return -1L

            // For other tabs, use the database ID.
            // Remember position 1 corresponds to trackingViews[0].
            val viewIndex = position - 1
            return if (viewIndex >= 0 && viewIndex < trackingViews.size) {
                trackingViews[viewIndex].tabViewId
            } else {
                RecyclerView.NO_ID // Safety fallback
            }
        }

        override fun containsItem(itemId: Long): Boolean {
            Log.i(TAG, "containsItem: itemId = $itemId")

            // The fixed tab ID (-1L) always exists.
            if (itemId == -1L) return true

            // Check if the database ID still exists in the list.
            return trackingViews.any { it.tabViewId == itemId }
        }

    }


    companion object {
        @JvmField
        val TAG = "TrackingTabsFragment"
        @JvmStatic
        fun newInstance(): TrackingTabsFragment {
            return TrackingTabsFragment()
        }
    }
}

