package com.atrainingtracker.trainingtracker.ui.aftermath

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.fragments.mapFragments.Roughness
import com.atrainingtracker.trainingtracker.fragments.mapFragments.TrackOnMapHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import java.util.concurrent.Executors

class TrackOnMapAftermathActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null

    private var workoutId: Long = -1L
    private val trackOnMapHelper by lazy { (application as TrainingApplication).trackOnMapHelper }

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

        supportActionBar?.title = "Workout Map"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mapView = findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        if (DEBUG) Log.i(TAG, "onMapReady")
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false // Location is static aftermath

        drawTrackAndMarkers()
    }

    // region Map Drawing Logic (Migrated from Fragment)
    private fun drawTrackAndMarkers() {
        showTrackOnMap(zoomToShowTrack = true)
        addStartMarker(true)
        addStopMarker()
        addExtremaMarkers()
    }

    private fun showTrackOnMap(zoomToShowTrack: Boolean) {
        googleMap ?: return // Don't proceed if the map isn't ready
        if (DEBUG) Log.i(TAG, "showTrackOnMap for workoutID=$workoutId")

        // showing all location sources (when the user wants it)
        if (TrainingApplication.showAllLocationSourcesOnMap()) {
            trackOnMapHelper.showTrackOnMap(null, googleMap, workoutId, Roughness.ALL, TrackOnMapHelper.TrackType.GPS, false, false)
            trackOnMapHelper.showTrackOnMap(null, googleMap, workoutId, Roughness.ALL, TrackOnMapHelper.TrackType.NETWORK, false, false)
            trackOnMapHelper.showTrackOnMap(null, googleMap, workoutId, Roughness.ALL, TrackOnMapHelper.TrackType.FUSED, false, false)
        }

        // Draw the main track
        trackOnMapHelper.showTrackOnMap(null, googleMap, workoutId, Roughness.ALL, TrackOnMapHelper.TrackType.BEST, zoomToShowTrack, true);
    }

    /**
     * Adds a start marker to the map.
     * Replaces `addStartMarker`.
     */
    private fun addStartMarker(zoomToStart: Boolean) {
        val latLngStart = getExtremaPosition(ExtremaType.START, calculateWhenNotInDb = true)
        latLngStart?.let {
            googleMap?.addMarker(
                MarkerOptions()
                    .position(it)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.start_logo_map))
                    .title(getString(R.string.Start))
            )
            if (zoomToStart) {
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 12f))
            }
        }
    }

    /**
     * Adds a stop marker to the map.
     * Replaces `addStopMarker`.
     */
    private fun addStopMarker() {
        val latLngStop = getExtremaPosition(ExtremaType.END, calculateWhenNotInDb = false)
        latLngStop?.let {
            googleMap?.addMarker(
                MarkerOptions()
                    .position(it)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.stop_logo_map))
                    .title(getString(R.string.Stop))
            )
        }
    }

    /**
     * Gets the LatLng position for START or END points of the workout.
     * Replaces `getExtremaPosition`.
     */
    private fun getExtremaPosition(extremaType: ExtremaType, calculateWhenNotInDb: Boolean): LatLng? {
        val baseFileName = WorkoutSummariesDatabaseManager.getBaseFileName(workoutId)

        var lat = WorkoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.LATITUDE, extremaType)
        if (lat == null && calculateWhenNotInDb) {
            lat = WorkoutSamplesDatabaseManager.calcExtremaValue(baseFileName, extremaType, SensorType.LATITUDE)
        }

        var lon = WorkoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.LONGITUDE, extremaType)
        if (lon == null && calculateWhenNotInDb) {
            lon = WorkoutSamplesDatabaseManager.calcExtremaValue(baseFileName, extremaType, SensorType.LONGITUDE)
        }

        return if (lat != null && lon != null) LatLng(lat, lon) else null
    }



    private fun addExtremaMarkers() {
        // Run marker creation on a background thread to avoid blocking the UI
        Executors.newSingleThreadExecutor().execute {
            for (sensorType in extremaSensorTypes) {
                when (sensorType) {
                    SensorType.ALTITUDE, SensorType.TEMPERATURE -> {
                        addExtremaMarkerToMap(sensorType, ExtremaType.MIN, null)
                        addExtremaMarkerToMap(sensorType, ExtremaType.MAX, null)
                    }
                    SensorType.CADENCE, SensorType.HR, SensorType.POWER, SensorType.SPEED_mps, SensorType.TORQUE -> {
                        addExtremaMarkerToMap(sensorType, ExtremaType.MAX, null)
                    }
                    SensorType.LINE_DISTANCE_m -> {
                        addExtremaMarkerToMap(sensorType, ExtremaType.MAX, R.drawable.max_line_distance_logo_map)
                    }
                    else -> {} // No markers for other types
                }
            }
        }
    }

    private fun addExtremaMarkerToMap(sensorType: SensorType, extremaType: ExtremaType, drawableId: Int?) {
        val latLngValue = WorkoutSamplesDatabaseManager.getExtremaPosition(workoutId, sensorType, extremaType)

        if (latLngValue != null) {
            val title = getString(
                R.string.location_extrema_format, extremaType.name,
                getString(sensorType.fullNameId),
                sensorType.myFormatter.format(latLngValue.value),
                getString(MyHelper.getShortUnitsId(sensorType))
            )
            val markerOptions = MarkerOptions().position(latLngValue.latLng).title(title)

            drawableId?.let {
                val markerBitmap = (ResourcesCompat.getDrawable(resources, it, null) as BitmapDrawable).bitmap
                markerOptions.icon(BitmapDescriptorFactory.fromBitmap(markerBitmap))
            }

            // Add marker on the main thread
            runOnUiThread {
                googleMap?.addMarker(markerOptions)
            }
        } else {
            if (DEBUG) Log.i(TAG, "No ${extremaType.name} data for ${sensorType.name} available.")
        }
    }

    private fun zoomToFitTrack(trackPoints: List<LatLng>) {
        if (trackPoints.isEmpty()) return
        val boundsBuilder = LatLngBounds.Builder()
        for (point in trackPoints) {
            boundsBuilder.include(point)
        }
        val bounds = boundsBuilder.build()
        // Use post to ensure layout has completed
        mapView.post {
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100)) // 100px padding
        }
    }
    // endregion

    // region Activity Lifecycle Management for MapView
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
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