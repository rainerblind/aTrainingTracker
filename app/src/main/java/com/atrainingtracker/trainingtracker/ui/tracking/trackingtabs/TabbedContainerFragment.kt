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
import com.atrainingtracker.trainingtracker.fragments.ControlTrackingFragment
import com.atrainingtracker.trainingtracker.fragments.TrackingFragmentClassic

class TabbedContainerFragment : Fragment() {

    private lateinit var viewModel: TabbedContainerViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: TrackingPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate a new layout for ViewPager2
        return inflater.inflate(R.layout.fragment_tabbed_container, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(TabbedContainerViewModel::class.java)

        // Assume a default or passed-in ActivityType for now
        val activityType = arguments?.getSerializable("ACTIVITY_TYPE") as? ActivityType ?: ActivityType.getDefaultActivityType()

        viewPager = view.findViewById(R.id.pager) // Ensure your XML has a ViewPager2 with this ID
        pagerAdapter = TrackingPagerAdapter(this, activityType)
        viewPager.adapter = pagerAdapter

        // Observe changes to the list of tracking views
        viewModel.trackingViews.observe(viewLifecycleOwner) { trackingViews ->
            pagerAdapter.updateTrackingViews(trackingViews)
        }

        // Load the initial data
        viewModel.loadTrackingViews(activityType)
    }

    private class TrackingPagerAdapter(fragment: Fragment, private val activityType: ActivityType) : FragmentStateAdapter(fragment) {
        private var trackingViews: List<TrackingViewInfo> = emptyList()

        fun updateTrackingViews(newViews: List<TrackingViewInfo>) {
            this.trackingViews = newViews
            notifyDataSetChanged() // Reload the ViewPager with the new data
        }

        // The first page is always the control fragment, followed by the tracking pages
        override fun getItemCount(): Int = 1 + trackingViews.size

        override fun createFragment(position: Int): Fragment {
            return if (position == 0) {
                ControlTrackingFragment()
            } else {
                val viewInfo = trackingViews[position - 1]
                // This will be replaced with our new modern TrackingFragment
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