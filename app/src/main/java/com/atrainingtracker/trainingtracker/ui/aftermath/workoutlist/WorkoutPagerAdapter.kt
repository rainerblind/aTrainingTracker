package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.atrainingtracker.banalservice.BSportType

class WorkoutPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    // Define the tabs order and their filter types
    private val tabs = listOf(
        null,               // All
        BSportType.BIKE,    // Bike
        BSportType.RUN,     // Run
        BSportType.UNKNOWN  // Other
    )

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        // Return your existing List Fragment, but with a filter argument
        return WorkoutListChildFragment.newInstance(tabs[position])
    }

    fun getTabType(position: Int) = tabs[position]
}