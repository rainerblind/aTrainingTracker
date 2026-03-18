package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow

class TrackingViewModel(
    private val repository: TrackingRepository,
    private val context: Context
) : ViewModel() {

    val trackingMode = repository.trackingMode

    val isSearching: Boolean = true // TODO get Live Data / Flow from Repository




    /**************************************************
     * Control Sport Type
     */
    // TODO Live Data or Flow for BSportType
    fun setSport(bSportType: BSportType) {
        // TODO: pass to repository
    }


    /**************************************************
     * Searching
     */
    // TODO: Provide Live Data or Flow for Searching

    fun onSearchClicked() {
        // TODO: pass to repository
    }

    fun isAntProperlyInstalled(): Boolean {
        // TODO: pass to repository
        return false
    }

    fun isBluetoothSupported(): Boolean {
        // TODO: pass to repository
        return false
    }

    fun onPairingClicked(protocol: Protocol) {
        // TODO: pass to repository
    }





    /***********************************************************************************************
     * Control Tracking
     */
    fun onStartTracking() {
        // TODO: pass to repository
    }

    fun onPauseTracking() {
        // TODO: pass to repository
    }

    fun onResumeTracking() {
        // TODO: pass to repository
    }

    fun onStopTracking() {
        // TODO: pass to repository
    }

}