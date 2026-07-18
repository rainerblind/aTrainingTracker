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

package com.atrainingtracker.trainingtracker.ui.settings.trackingtabs

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.interfaces.StartOrResumeInterface
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsFragment
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModel
import com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModelFactory


/**
 * An activity that is used to configure the tracking tabs.
 * Thereby, the user selects the ActivityType
 */
class ConfigTrackingTabsActivity : AppCompatActivity(),
    BANALService.GetBanalServiceInterface,
    StartOrResumeInterface {

    private val viewModel: TrackingTabsViewModel by viewModels {
        TrackingTabsViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.main_activity_without_navigation)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.screenMode.value == com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode.CONFIGURATION) {
                    viewModel.setScreenMode(com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode.PREVIEW)
                } else {
                    viewModel.exitConfiguration()
                    finish()
                }
            }
        })

        // Now, show a dialog to select the activity type
        ActivityTypeSelectionHelper.showSelectionDialog(
            fragmentManager = supportFragmentManager,
            onTypeSelected = { showTrackingTabs(it) },
            onCancel = { finish() }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // State cleanup is now handled in OnBackPressedCallback for immediate reset
    }

    // Handle the back button in the toolbar
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showTrackingTabs(activityType: ActivityType) {
        // Create the fragment and pass the ActivityType explicitly
        val fragment = TrackingTabsFragment.newInstance(activityType)

        supportFragmentManager.beginTransaction()
            .replace(R.id.content, fragment)
            .commit()
    }

    // --- Implementation of BANALService.GetBanalServiceInterface ---
    // (Maintains compatibility with legacy components if they look for this activity)
    // TODO: Remove this dependency.
    override fun getBanalServiceComm(): BANALService.BANALServiceComm? {
        // Return null as in the classic version; modern fragments should use ViewModel/Repository
        return null
    }

    override fun registerConnectionStatusListener(listener: BANALService.GetBanalServiceInterface.ConnectionStatusListener?) {
        // Dummy implementation as in classic version
    }

    // --- StartOrResumeInterface ---
    override fun showStartOrResumeDialog() {}

    override fun chooseStart() {}

    override fun chooseResume() {}
}