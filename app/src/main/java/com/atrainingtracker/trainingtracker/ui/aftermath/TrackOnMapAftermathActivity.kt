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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.constraintlayout.widget.ConstraintLayout
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderViewHolder
import com.atrainingtracker.trainingtracker.ui.map.TrackOnMapAftermathViewModel
import com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors

class TrackOnMapAftermathActivity : AppCompatActivity() {

    private val viewModel: TrackOnMapAftermathViewModel by viewModels()

    private lateinit var workoutHeaderViewHolder: WorkoutHeaderViewHolder


    private var workoutId: Long = -1L

    private val extremaSensorTypes = arrayOf(
        SensorType.ALTITUDE, SensorType.CADENCE, SensorType.HR, SensorType.LINE_DISTANCE_m,
        SensorType.POWER, SensorType.SPEED_mps, SensorType.TEMPERATURE, SensorType.TORQUE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_on_map_aftermath)

        workoutId = intent.getLongExtra(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_ID, -1L)
        if (workoutId == -1L) {
            Log.e(TAG, "No workout ID provided. Finishing activity.")
            finish()
            return
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // create the header
        val headerView = findViewById<ConstraintLayout>(R.id.workout_header)
        workoutHeaderViewHolder = WorkoutHeaderViewHolder(headerView)
        populateHeader()


        // 1. Setup the Compose Map Container
        val composeContainer = findViewById<ComposeView>(R.id.map_compose_container)
        composeContainer.setContent {
            ATrainingTrackerTheme {
                // Observe the MapState from the ViewModel
                val mapState by viewModel.aftermathState.collectAsState()
                // For aftermath, we don't have a live location flow
                val noLocation = remember { MutableStateFlow<LatLng?>(null) }

                var selectedDistance by remember { mutableStateOf<Double?>(null) }

                Column(modifier = Modifier.fillMaxSize()) {
                    // The Map takes the top 70% of the screen
                    ATrainingTrackerMap(
                        mapState = mapState,
                        currentLocationFlow = noLocation,
                        selectedDistance = selectedDistance,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.75f) // Adjust this ratio as needed
                    )

                    // The Elevation Profile takes the bottom 30%
                    // We extract the track points from the mapState
                    ElevationProfile(
                        pathPoints = mapState.tracks.firstOrNull()?.path ?: emptyList(),                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.25f),
                        currentDistance = selectedDistance,
                        // Callback when the user slides their finger
                        onDistanceSelected = { dist ->
                            selectedDistance = dist
                        }
                    )
                }
            }
        }

        // 2. Load the Data
        // This triggers the DB queries for tracks and extrema markers
        viewModel.loadAftermathData(workoutId)
    }

    private fun populateHeader() {
        // Use a background thread to fetch data to avoid blocking the UI
        Executors.newSingleThreadExecutor().execute {
            // Background thread: Fetch the data
            val workoutHeaderDataProvider = WorkoutHeaderDataProvider(
                this,
                EquipmentDbHelper(this),
                SportTypeDatabaseManager.getInstance(this)
            )
            val workoutHeaderData = workoutHeaderDataProvider.createWorkoutHeaderData(workoutId)

            // Switch back to the main thread to update the UI
            runOnUiThread {
                if (workoutHeaderData != null) {
                    workoutHeaderViewHolder.bind(workoutHeaderData)
                } else {
                    Log.w(TAG, "Could not load workout data for header population.")
                }
            }
        }
    }


    override fun onSupportNavigateUp(): Boolean {
        finish() // Or onBackPressed()
        return true
    }
    // endregion

    companion object {
        private val TAG = TrackOnMapAftermathActivity::class.java.simpleName
        private const val DEBUG = true // Or TrainingApplication.getDebug(false)

        // Static helper method to start this activity easily
        @JvmStatic
        fun start(context: Context, workoutId: Long) {
            val intent = Intent(context, TrackOnMapAftermathActivity::class.java).apply {
                putExtra(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_ID, workoutId)
            }
            context.startActivity(intent)
        }
    }
}