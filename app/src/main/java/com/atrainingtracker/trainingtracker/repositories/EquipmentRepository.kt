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

package com.atrainingtracker.trainingtracker.repositories

import android.app.Application
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class EquipmentRepository private constructor(private val application: Application) :
    CoroutineScope {


    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job

    // Helper instances, initialized lazily
    private val equipmentDbHelper by lazy { EquipmentDbHelper(application) }

    val equipmentList: List<EquipmentDbHelper.EquipmentData>

    init {
        equipmentList = equipmentDbHelper.getEquipmentItems() as List<EquipmentDbHelper.EquipmentData>
    }

    companion object {
        private val TAG = "EquipmentRepository"
        private val DEBUG = TrainingApplication.getDebug(true)

        private val _isSyncing = MutableStateFlow(false)

        @JvmStatic
        val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

        @JvmStatic
        @Synchronized
        fun setSyncing(syncing: Boolean) {
            _isSyncing.value = syncing
        }

        @androidx.annotation.VisibleForTesting
        fun resetSyncingForTesting() {
            _isSyncing.value = false
        }

        @Volatile
        private var _equipmentLinksChanged = MutableSharedFlow<Long?>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        /**
         * Reactive event stream emitting the device ID of affected sensors whenever
         * equipment-to-sensor mappings are modified in SQLite (REQ-UI-257).
         * Emits null when a bulk or equipment-level modification occurs.
         */
        @JvmStatic
        val equipmentLinksChanged: SharedFlow<Long?>
            get() = _equipmentLinksChanged.asSharedFlow()

        /**
         * Dispatches an equipment link mutation event across the application (REQ-UI-257).
         *
         * @param affectedDeviceId Optional ID of the specific sensor whose links changed,
         * or null for bulk equipment updates/deletions.
         */
        @JvmStatic
        fun notifyEquipmentLinksChanged(affectedDeviceId: Long? = null) {
            try {
                _equipmentLinksChanged.tryEmit(affectedDeviceId)
            } catch (t: Throwable) {
                if (DEBUG) Log.w(TAG, "Failed to emit equipmentLinksChanged event: ${t.message}")
            }
        }

        // The single, volatile instance of the repository.
        // @Volatile guarantees that writes to this field are immediately visible to other threads.
        @Volatile
        private var INSTANCE: EquipmentRepository? = null

        /**
         * Gets the singleton instance of the EquipmentRepository.
         *
         * @param application The application context, needed to create the instance for the first time.
         * @return The single instance of EquipmentRepository.
         */
        fun getInstance(application: Application): EquipmentRepository {
            // Double-check locking ensures thread safety and performance.
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE
                if (instance != null) {
                    instance
                } else {
                    val newInstance = EquipmentRepository(application)
                    INSTANCE = newInstance
                    newInstance
                }
            }
        }

        @androidx.annotation.VisibleForTesting
        fun resetForTesting(newInstance: EquipmentRepository? = null) {
            INSTANCE = newInstance
            _equipmentLinksChanged = MutableSharedFlow(
                extraBufferCapacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }
    }
}

/**
 * Extension property providing seamless instance access to [EquipmentRepository.isSyncing].
 */
val EquipmentRepository.isSyncing: StateFlow<Boolean>
    get() = EquipmentRepository.isSyncing