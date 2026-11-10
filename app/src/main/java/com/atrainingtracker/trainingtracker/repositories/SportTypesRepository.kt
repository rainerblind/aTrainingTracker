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
import android.content.Context
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.getValue

class SportTypesRepository private constructor(private val application: Application) :
    CoroutineScope {


    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job

    // Helper instances, initialized lazily
    private val sportTypeDatabaseManager by lazy { SportTypeDatabaseManager.getInstance(application) }

    val sportTypesList: List<SportTypeDatabaseManager.SimpleSportTypeInfo>

    init {
        sportTypesList = sportTypeDatabaseManager.getSportTypes(BSportType.UNKNOWN)
    }

    /**
     * Resolves localized UI display name for sport type id (REQ-UI-507.5).
     */
    fun getUIName(sportId: Long): String = sportTypeDatabaseManager.getUIName(sportId)

    /**
     * Resolves base sport type for sport type id (REQ-UI-507.5).
     */
    fun getBSportType(sportId: Long): BSportType = sportTypeDatabaseManager.getBSportType(sportId)

    /**
     * Resolves list of sport types matching [bSportType] (or all if null) (REQ-UI-507.5).
     */
    fun getSportTypes(bSportType: BSportType?): List<SportTypeDatabaseManager.SimpleSportTypeInfo> =
        sportTypeDatabaseManager.getSportTypes(bSportType)

    companion object {
        private val TAG = "SportTypesRepository"
        private val DEBUG = TrainingApplication.getDebug(true)

        // The single, volatile instance of the repository.
        // @Volatile guarantees that writes to this field are immediately visible to other threads.
        @Volatile
        private var INSTANCE: SportTypesRepository? = null

        /**
         * Gets the singleton instance of the SportTypesRepository.
         *
         * @param application The application context, needed to create the instance for the first time.
         * @return The single instance of SportTypesRepository.
         */
        fun getInstance(application: Application): SportTypesRepository {
            // Double-check locking ensures thread safety and performance.
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE
                if (instance != null) {
                    instance
                } else {
                    val newInstance = SportTypesRepository(application)
                    INSTANCE = newInstance
                    newInstance
                }
            }
        }

        @JvmStatic
        fun getInstance(context: Context): SportTypesRepository {
            return getInstance(context.applicationContext as Application)
        }

        /**
         * Resolves canonical sport type ID for a base sport type (REQ-UI-507.5).
         */
        @JvmStatic
        fun getSportTypeId(bSportType: BSportType): Long = SportTypeDatabaseManager.getSportTypeId(bSportType)

        @androidx.annotation.VisibleForTesting
        fun resetForTesting(newInstance: SportTypesRepository? = null) {
            INSTANCE = newInstance
        }
    }

}