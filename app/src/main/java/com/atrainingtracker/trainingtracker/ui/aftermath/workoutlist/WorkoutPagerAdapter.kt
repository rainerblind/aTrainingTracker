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
        return WorkoutSummariesListFragment.newInstance(tabs[position])
    }

    fun getTabType(position: Int) = tabs[position]
}