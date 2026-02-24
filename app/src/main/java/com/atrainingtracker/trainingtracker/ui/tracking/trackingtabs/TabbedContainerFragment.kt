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
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.fragments.ControlTrackingFragment
import com.atrainingtracker.trainingtracker.fragments.TrackingFragmentClassic
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class TabbedContainerFragment : Fragment() {

    private lateinit var viewModel: TabbedContainerViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var pagerAdapter: TrackingPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tabbed_container, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(TabbedContainerViewModel::class.java)

        val activityType = arguments?.getSerializable("ACTIVITY_TYPE") as? ActivityType ?: ActivityType.getDefaultActivityType()

        // Initialize views
        pagerAdapter = TrackingPagerAdapter(this, activityType)
        viewPager = view.findViewById(R.id.pager)
        tabLayout = view.findViewById(R.id.tab_layout)
        viewPager.adapter = pagerAdapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            // This lambda is called for each tab to set its title.
            // We get the title from our adapter.
            tab.text = pagerAdapter.getPageTitle(position)
        }.attach() // This is the magic call that links them.

        // Observe changes to the list of tracking views from the ViewModel
        viewModel.trackingViews.observe(viewLifecycleOwner) { trackingViews ->
            pagerAdapter.updateTrackingViews(trackingViews)
        }

        // Load the initial data
        viewModel.loadTrackingViews(activityType)
    }

    private class TrackingPagerAdapter(private val fragment: Fragment, private val activityType: ActivityType) : FragmentStateAdapter(fragment) {
        private var trackingViews: List<TrackingViewInfo> = emptyList()

        fun updateTrackingViews(newViews: List<TrackingViewInfo>) {
            this.trackingViews = newViews
            notifyDataSetChanged()
        }

        fun getPageTitle(position: Int): CharSequence {
            return if (position == 0) {
                if (TrainingApplication.isTracking()) {
                    if (TrainingApplication.isPaused()) {
                        return fragment.getString(R.string.Paused);
                    } else {
                        return fragment.getString(R.string.Tracking);
                    }
                } else {
                    return fragment.getString(R.string.tab_start);
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
                TrackingFragmentClassic.newInstance(viewInfo.id, activityType)
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(activityType: ActivityType, selectedItem: Int): TabbedContainerFragment {
            val fragment = TabbedContainerFragment()
            fragment.arguments = Bundle().apply {
                putSerializable("ACTIVITY_TYPE", activityType)
                putInt("SELECTED_ITEM", selectedItem)
            }
            return fragment
        }
    }
}