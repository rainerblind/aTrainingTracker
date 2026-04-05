package com.atrainingtracker.trainingtracker.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.segments.MapSegment
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager
import com.atrainingtracker.trainingtracker.ui.tracking.BANALServiceRepository
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.Polyline
import kotlinx.coroutines.flow.*


class MapFragmentWithTrackViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BANALServiceRepository.getInstance(application)

    // Segments from DB
    private val starredSegments: List<MapSegment> = SegmentsDatabaseManager.getInstance(application).allSegments

    // --- Simplified Reactive State ---
    val mapState: StateFlow<MapState> = repository.currentTrack
        .map { track ->
            // --- Logic for Start Marker ---
            val markers = if (track.isNotEmpty()) {
                listOf(
                    LocationMarker(
                        position = track.first(),
                        iconResId = R.drawable.start_logo_map,
                        title = application.getString(R.string.Start)
                    )
                )
            } else {
                emptyList()
            }

            MapState(
                segments = starredSegments,
                currentTrack = track,
                bearing = 0f,           // Forced zero
                speed = 0f,             // Forced zero
                isFollowMeEnabled = false, // Always off
                markers = markers
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MapState(
                segments = starredSegments,
                currentTrack = emptyList(),
                bearing = 0f,
                speed = 0f,
                isFollowMeEnabled = false,
                markers = emptyList()
            )
        )

    // Exposed for the User Marker specifically (blue dot still moves, camera does not)
    val currentLocation: StateFlow<LatLng?> = repository.currentLocation
}