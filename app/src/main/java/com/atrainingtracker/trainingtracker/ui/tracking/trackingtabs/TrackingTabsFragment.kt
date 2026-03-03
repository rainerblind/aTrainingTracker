package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.CheckBox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
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

    /*
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        // Do not show the configure button if we are on the first (control tracking) tab
        val currentTab = viewPager.currentItem
        val isFirstTab = currentTab == 0
        menu.findItem(R.id.action_configure)?.apply {
            isVisible = !isFirstTab
        }
    }
     */

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

        val tabDivider = view.findViewById<View>(R.id.tab_divider)

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

        // --- BACK BUTTON HANDLING ---
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
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
            // This observer will be triggered on initial load and whenever the activity type changes.
            if (!::pagerAdapter.isInitialized) {
                // First-time setup
                pagerAdapter = TrackingPagerAdapter(this, activityType)
                viewPager.adapter = pagerAdapter

                // Add a page change callback to control button visibility
                viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        updateLapButtonVisibility()
                    }
                })
            } else {
                // If the adapter already exists, just update its activityType.
                // The trackingViews observer below will handle updating the actual pages.
                pagerAdapter.setActivityType(activityType)
            }

            // Trigger an initial refresh of the tabs now that the adapter exists
            attachTabLayoutMediator(viewModel.screenMode.value)
        }

        // Observe the list of tracking views from the ViewModel.
        // The ViewModel's `switchMap` ensures this LiveData automatically updates
        // when the `activityType` changes.
        viewModel.trackingViews.observe(viewLifecycleOwner) { trackingViews ->
            if (::pagerAdapter.isInitialized) {
                pagerAdapter.updateTrackingViews(trackingViews)
                updateLapButtonVisibility()  // when the trackingViews change (due to a change of the activity type), it must be reevaluated whether to show the button
            }
        }

        // Observe TrackingMode to update the tab title ---
        viewModel.trackingMode.observe(viewLifecycleOwner) { state ->
            // When the state changes, just update the title of the first tab.
            tabLayout.getTabAt(0)?.text = pagerAdapter.getPageTitle(0)
        }

        // Observe ScreenMode to swap between Tracking and Configuration UI
        lifecycleScope.launch {
            viewModel.screenMode.collect { mode ->
                // Force the menu to update (shows/hides toggle icon)
                requireActivity().invalidateOptionsMenu()

                // Visual separation: Change divider color when in Config mode
                tabDivider.setBackgroundColor(
                    if (mode == ScreenMode.CONFIGURATION)
                        requireContext().getColor(android.R.color.holo_red_dark)
                    else
                        requireContext().getColor(android.R.color.darker_gray)
                )

                // Re-attach mediator to refresh all tabs with either Text or CustomView
                attachTabLayoutMediator(mode)
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

    private fun attachTabLayoutMediator(mode: ScreenMode) {
        // SAFETY CHECK: Prevent crash if adapter isn't ready yet
        if (viewPager.adapter == null) return

        // We must re-create the mediator whenever we change modes
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            val isFirstTab = position == 0

            if (mode == ScreenMode.CONFIGURATION && !isFirstTab) {
                // Inflate your custom layout
                val configView = layoutInflater.inflate(R.layout.layout_tab_config, null)
                val viewInfo = pagerAdapter.getTrackingViewInfo(position) ?: return@TabLayoutMediator

                // Bind UI Elements
                val nameEdit = configView.findViewById<EditText>(R.id.edit_tab_name)
                val cbMap = configView.findViewById<CheckBox>(R.id.cb_show_map)
                val cbLap = configView.findViewById<CheckBox>(R.id.cb_show_lap)
                val btnBefore = configView.findViewById<Button>(R.id.btn_add_before)
                val btnAfter = configView.findViewById<Button>(R.id.btn_add_after)
                val btnDelete = configView.findViewById<Button>(R.id.btn_delete_tab)

                // Set values
                nameEdit.setText(viewInfo.name)
                cbMap.isChecked = viewInfo.showMap
                cbLap.isChecked = viewInfo.showLapButton

                // Set Listeners
                nameEdit.addTextChangedListener(object : android.text.TextWatcher {
                    override fun afterTextChanged(s: android.text.Editable?) {
                        viewModel.onUpdateTabName(viewInfo.tabViewId, s.toString())
                    }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })

                cbMap.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.onUpdateTabSettings(viewInfo.tabViewId, isChecked, cbLap.isChecked)
                }
                cbLap.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.onUpdateTabSettings(viewInfo.tabViewId, cbMap.isChecked, isChecked)
                }

                btnBefore.setOnClickListener { viewModel.onAddTabRelative(viewInfo.tabViewId, false) }
                btnAfter.setOnClickListener { viewModel.onAddTabRelative(viewInfo.tabViewId, true) }
                btnDelete.setOnClickListener { viewModel.onDeleteTab(viewInfo.tabViewId) }

                tab.customView = configView
            } else {
                // Standard Text Tab
                tab.text = pagerAdapter.getPageTitle(position)
                tab.customView = null
            }
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
        private val fragment: Fragment,
        private var activityType: ActivityType
    ) : FragmentStateAdapter(fragment) {

        private var trackingViews: List<TrackingViewInfo> = emptyList()
        private val viewModel: TrackingTabsViewModel by lazy {
            ViewModelProvider(fragment).get(TrackingTabsViewModel::class.java)
        }

        fun setActivityType(newActivityType: ActivityType) {
            this.activityType = newActivityType
            // The logic to update pages is handled by the trackingViews observer,
            // which will call updateTrackingViews.
        }

        fun updateTrackingViews(newViews: List<TrackingViewInfo>) {
            this.trackingViews = newViews
            notifyDataSetChanged()

            // Trigger the fragment to re-attach the mediator to show the new tabs
            (fragment as? TrackingTabsFragment)?.let {
                it.attachTabLayoutMediator(it.viewModel.screenMode.value)
            }
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
    }

    companion object {
        @JvmField
        val TAG = "TabbedContainerFragment"
        @JvmStatic
        fun newInstance(): TrackingTabsFragment {
            return TrackingTabsFragment()
        }
    }
}