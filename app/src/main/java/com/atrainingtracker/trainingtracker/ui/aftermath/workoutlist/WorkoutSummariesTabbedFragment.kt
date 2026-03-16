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

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.DeletionProgress
import com.google.android.material.tabs.TabLayoutMediator

/**
 * A fragment that displays a list of workout summaries using a modern,
 * ViewModel-driven architecture with a RecyclerView.
 */
class WorkoutSummariesTabbedFragment : Fragment() {

    // Use the Kotlin property delegate for a cleaner ViewModel initialization.
    private val viewModel: WorkoutSummariesViewModel by viewModels()

    private lateinit var progressContainer: View // Will hold the ProgressBar and TextView

    private lateinit var tabLayout: com.google.android.material.tabs.TabLayout
    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var progressText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()

        // 1. Root FrameLayout (to allow overlaying the progress bar)
        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        // 2. Main Content Container (Tabs + Pager)
        val contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        tabLayout = com.google.android.material.tabs.TabLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            tabMode = com.google.android.material.tabs.TabLayout.MODE_SCROLLABLE
        }

        viewPager = androidx.viewpager2.widget.ViewPager2(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            // Essential: Generate a unique ID so FragmentManager can save/restore state
            id = View.generateViewId()
        }

        contentContainer.addView(tabLayout)
        contentContainer.addView(viewPager)

        // --- Create a container for the progress indicators ---
        progressContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE // Initially hidden

            setBackgroundResource(R.drawable.progress_container_background)

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )

            // Add some padding inside the container
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)

            // Add the ProgressBar to the container
            addView(ProgressBar(context))

            // Add the TextView for progress text
            progressText = TextView(context).apply {
                setTextAppearance(android.R.style.TextAppearance_Material_Body2)
                setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0) // 8dp top padding
                gravity = Gravity.CENTER_HORIZONTAL
            }
            addView(progressText)
        }

        // Add everything to root
        root.addView(contentContainer)
        root.addView(progressContainer)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pagerAdapter = WorkoutPagerAdapter(this)
        viewPager.adapter = pagerAdapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (pagerAdapter.getTabType(position)) {
                null -> getString(R.string.workout_summaries_tab_all)
                BSportType.RUN -> getString(R.string.workout_summaries_tab_run)
                BSportType.BIKE -> getString(R.string.workout_summaries_tab_bike)
                else -> getString(R.string.workout_summaries_tab_other)
            }
        }.attach()

        setupMenu()
        observeViewModel()
    }

    private fun setupMenu() {
        // Add the MenuProvider to the Fragment's Lifecycle
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Inflate the menu resource file
                menuInflater.inflate(R.menu.workout_summaries_list_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.menu_delete_old_workouts -> {
                        viewModel.onDeleteOldWorkoutsClicked()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun observeViewModel() {
        // Observe loading state to show/hide progress
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                progressContainer.visibility = View.VISIBLE
                progressText.text = getString(R.string.workout_summaries_loading)
            }
            else {
                progressContainer.visibility = View.GONE
            }
        }


        viewModel.showDeleteOldWorkoutsDialogEvent.observe(viewLifecycleOwner) {
            showDeleteOldWorkoutsDialog()
        }

        viewModel.deletionProgress.observe(viewLifecycleOwner) { state ->
            when (state) {
                is DeletionProgress.Idle -> {
                    progressContainer.visibility = View.GONE
                    viewPager.alpha = 1.0f
                }
                is DeletionProgress.InProgress -> {
                    progressContainer.visibility = View.VISIBLE
                    progressText.text = getString(R.string.deleting_workout, state.workoutName)
                    viewPager.alpha = 0.5f // Keep the list dimmed
                }
            }
        }

    }

    private fun showDeleteOldWorkoutsDialog() {
        val context = requireContext()

        // Create an EditText for the user to input the number of days.
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(R.string.defaultDaysToKeep)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }

        // We need a container to add some padding around the EditText.
        val container = FrameLayout(context).apply {
            val padding = (20 * resources.displayMetrics.density).toInt() // 20dp
            setPadding(padding, 0, padding, 0)
            addView(input)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.deleteOldWorkouts)
            .setMessage(R.string.deleteWorkoutsThatAreOlderThanDays)
            .setView(container) // Set the container with the EditText
            .setPositiveButton(R.string.OK) { _, _ ->
                // When the user clicks OK, parse the input and call the ViewModel.
                val daysToKeep = input.text.toString().toIntOrNull()
                if (daysToKeep != null) {
                    viewModel.executeDeleteOldWorkouts(daysToKeep)
                }
            }
            .setNegativeButton(R.string.Cancel, null) // Do nothing on cancel
            .show()
    }


    companion object {
        @JvmField
        var TAG: String = "WorkoutSummariesListFragment"
    }
}