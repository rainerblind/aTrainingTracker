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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.trainingtracker.interfaces.StartOrResumeInterface


/**
 * An activity that is used to configure the tracking tabs.
 * Thereby, the user selects the ActivityType
 */
class ConfigTrackingTabsActivity : AppCompatActivity(),
    BANALService.GetBanalServiceInterface,
    StartOrResumeInterface {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.main_activity_without_navigation)

        // Find the toolbar from the XML layout
        val toolbar: Toolbar = findViewById(R.id.apps_toolbar)

        // Set it as the ActionBar for this Activity
        // This allows getSupportActionBar() to work and populates the Menu
        setSupportActionBar(toolbar)

        // Add a back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // supportActionBar?.setTitle(R.string.prefsConfigureDisplaysTitle)


        // Now, show an dialog to select the activity type
        showSelectActivityTypeDialog()
    }

    // Handle the back button in the toolbar
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showSelectActivityTypeDialog() {
        val types = ActivityType.values()

        // Define a simple ArrayAdapter with an icon
        val adapter = object : android.widget.ArrayAdapter<ActivityType>(
            this,
            android.R.layout.select_dialog_item,
            android.R.id.text1,
            types
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val v = super.getView(position, convertView, parent)
                val tv = v.findViewById<android.widget.TextView>(android.R.id.text1)

                val type = getItem(position)
                if (type != null) {
                    tv.text = getString(type.titleId)
                    // Set the icon to the left of the text
                    tv.setCompoundDrawablesWithIntrinsicBounds(type.logoId, 0, 0, 0)
                    // Add some padding between icon and text
                    tv.compoundDrawablePadding = 32
                }
                return v
            }
        }

        // Create a Custom Title View
        val titleView = android.widget.TextView(this).apply {
            setText(R.string.choose_activity_type)
            setPadding(40, 40, 40, 40)
            textSize = 22f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.color_primary))
            gravity = android.view.Gravity.CENTER
        }

        // 3. Build the Dialog using the custom title and adapter
        AlertDialog.Builder(this)
            .setCustomTitle(titleView) // This replaces the standard white header
            .setAdapter(adapter) { _, which ->
                val selection = types[which]
                showTrackingTabs(selection)
            }
            .setOnCancelListener { finish() }
            .show()
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