package com.atrainingtracker.trainingtracker.ui.tracking.tracking


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.GoogleMap

class TrackingMapViewModel(application: Application) : AndroidViewModel(application) {

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