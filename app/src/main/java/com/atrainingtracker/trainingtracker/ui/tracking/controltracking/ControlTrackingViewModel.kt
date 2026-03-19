package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.content.Context
import androidx.lifecycle.ViewModel
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.trainingtracker.ui.tracking.BANALServiceRepository

class ControlTrackingViewModel(
    private val repository: BANALServiceRepository,
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

    /*
     * Remote devices
     */
    // TODO Live Data or Flow for RemoteDevices
    fun onDeviceClicked(device: RemoteDevice) {
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