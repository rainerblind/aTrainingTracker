package com.atrainingtracker.trainingtracker.ui.tracking.tracking


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor

class TrackingMapViewModel(application: Application) : AndroidViewModel(application) {

    var userMarker: com.google.android.gms.maps.model.Marker? = null
    var trackPolyline: com.google.android.gms.maps.model.Polyline? = null

    // has for the 'static' segments and markers.
    var staticDataHash: Int =0

    // The single MapView instance shared by all fragments
    val sharedMapView: MapView by lazy {
        MapView(application, GoogleMapOptions().apply {
            mapType(GoogleMap.MAP_TYPE_TERRAIN)
        }).apply {
            onCreate(null) // Initialize the lifecycle
            onStart()
            onResume()
        }
    }

    // 2. Persistent state to prevent "Zoom from Space" on every swipe
    // This allows the map to "remember" where it was even when not visible
    var isInitialPositionSet: Boolean = false

    // Clean up when the activity finally dies
    override fun onCleared() {
        super.onCleared()
        sharedMapView.onPause()
        sharedMapView.onStop()
        sharedMapView.onDestroy()
    }
}


class TrackingMapViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrackingMapViewModel::class.java)) {
            return TrackingMapViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}