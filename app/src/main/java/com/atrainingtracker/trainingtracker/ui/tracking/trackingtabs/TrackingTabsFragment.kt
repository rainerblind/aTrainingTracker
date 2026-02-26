package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.dialogs.LapSummaryDialog
import com.atrainingtracker.trainingtracker.fragments.ControlTrackingFragment
import com.atrainingtracker.trainingtracker.ui.tracking.LapEvent
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewInfo
import com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class TrackingTabsFragment : Fragment() {

    private lateinit var viewModel: TrackingTabsViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var pagerAdapter: TrackingPagerAdapter

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

        // Observe the ActivityType from the ViewModel (which gets it from the repository)
        viewModel.activityType.observe(viewLifecycleOwner) { activityType ->
            // This observer will be triggered on initial load and whenever the activity type changes.
            if (!::pagerAdapter.isInitialized) {
                // First-time setup
                pagerAdapter = TrackingPagerAdapter(this, activityType)
                viewPager.adapter = pagerAdapter

                // Link the TabLayout and the ViewPager2 *after* the adapter is set.
                TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                    tab.text = pagerAdapter.getPageTitle(position)
                }.attach()
            } else {
                // If the adapter already exists, just update its activityType.
                // The trackingViews observer below will handle updating the actual pages.
                pagerAdapter.setActivityType(activityType)
            }
        }

        // Observe the list of tracking views from the ViewModel.
        // The ViewModel's `switchMap` ensures this LiveData automatically updates
        // when the `activityType` changes.
        viewModel.trackingViews.observe(viewLifecycleOwner) { trackingViews ->
            if (::pagerAdapter.isInitialized) {
                pagerAdapter.updateTrackingViews(trackingViews)
            }
        }

        // Observe TrackingMode to update the tab title ---
        viewModel.trackingMode.observe(viewLifecycleOwner) { state ->
            // When the state changes, just update the title of the first tab.
            tabLayout.getTabAt(0)?.text = pagerAdapter.getPageTitle(0)
        }

        viewModel.lapEvent.observe(viewLifecycleOwner) { lapEvent ->
            // Check that the control tab isn't active and that we have a valid event
            if (viewPager.currentItem != 0 && lapEvent != null) {
                showLapSummaryDialog(lapEvent)
            }
        }
    }

    // --- Helper method to show the dialog ---
    private fun showLapSummaryDialog(lapEvent: LapEvent) {
        if (!isAdded) return // Ensure fragment is still attached to the activity

        val lapSummaryDialog = LapSummaryDialog.newInstance(
            lapEvent.lapNumber,
            lapEvent.lapTime,
            lapEvent.lapDistance,
            lapEvent.lapSpeed
        )
        // Use childFragmentManager for dialogs shown from within a Fragment
        lapSummaryDialog.show(childFragmentManager, LapSummaryDialog.TAG)
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

        override fun getItemCount(): Int = 1 + trackingViews.size

        override fun createFragment(position: Int): Fragment {
            return if (position == 0) {
                ControlTrackingFragment()
            } else {
                val viewInfo = trackingViews[position - 1]
                TrackingFragment.newInstance(viewInfo.tabViewId)
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