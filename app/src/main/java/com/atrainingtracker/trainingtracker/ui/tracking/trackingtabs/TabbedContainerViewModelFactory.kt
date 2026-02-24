package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingRepository


class TabbedContainerViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TabbedContainerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TabbedContainerViewModel(application, TrackingRepository.getInstance(application)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}