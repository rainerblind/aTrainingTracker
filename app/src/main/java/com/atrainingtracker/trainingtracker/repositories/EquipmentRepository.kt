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
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    }

}