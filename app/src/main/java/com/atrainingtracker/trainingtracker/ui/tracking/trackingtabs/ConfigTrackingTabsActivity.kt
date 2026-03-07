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
import com.atrainingtracker.trainingtracker.interfaces.RemoteDevicesSettingsInterface
import com.atrainingtracker.trainingtracker.interfaces.StartOrResumeInterface


/**
 * An activity that is used to configure the tracking tabs.
 * Thereby, the user selects the ActivityType
 */
class ConfigTrackingTabsActivity : AppCompatActivity(),
    BANALService.GetBanalServiceInterface,
    RemoteDevicesSettingsInterface,
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
        val typeTitles = types.map { getString(it.titleId) }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.choose_activity_type)
            .setItems(typeTitles) { _, which ->
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

    // --- RemoteDevicesSettingsInterface (StartPairingListener) ---
    override fun startPairing(protocol: Protocol?) {
        Log.i("ConfigActivity", "Pairing requested for $protocol")
        // Implementation: Usually opens the pairing activity
    }

    override fun enableBluetoothRequest() {
        // Implementation: Trigger system Bluetooth dialog
    }

    // --- StartOrResumeInterface ---
    override fun showStartOrResumeDialog() {}

    override fun chooseStart() {}

    override fun chooseResume() {}
}