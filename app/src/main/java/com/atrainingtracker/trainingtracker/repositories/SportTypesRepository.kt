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

class SportTypesRepository private constructor(private val context: Context) :
    CoroutineScope {


    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job

    private val sportTypeDatabaseManager get() = SportTypeDatabaseManager.getInstance(context)

    val sportTypesList: List<SportTypeDatabaseManager.SimpleSportTypeInfo>
        get() = sportTypeDatabaseManager.getSportTypes(BSportType.UNKNOWN)

    /**
     * Resolves localized UI display name for sport type id (REQ-UI-507.5).
     */
    fun getUIName(sportId: Long): String = sportTypeDatabaseManager.getUIName(sportId)

    /**
     * Resolves base sport type for sport type id (REQ-UI-507.5).
     */
    fun getBSportType(sportId: Long): BSportType = sportTypeDatabaseManager.getBSportType(sportId)

    /**
     * Resolves TCX activity name for sport type id (REQ-DAT-041.4).
     */
    fun getTcxName(sportId: Long): String? = sportTypeDatabaseManager.getTcxName(sportId)

    /**
     * Resolves Strava sport type name for sport type id (REQ-DAT-041.3).
     */
    fun getStravaName(sportId: Long): String? = sportTypeDatabaseManager.getStravaName(sportId)

    /**
     * Resolves sport type ID from Strava activity type name (REQ-DAT-041.3).
     */
    fun getSportTypeIdFromStravaName(stravaName: String?): Long = sportTypeDatabaseManager.getSportTypeIdFromStravaName(stravaName)

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
         * @param context The context, needed to create the instance for the first time.
         * @return The single instance of SportTypesRepository.
         */
        @JvmStatic
        fun getInstance(application: Application): SportTypesRepository {
            return getInstance(application as Context)
        }

        @JvmStatic
        fun getInstance(context: Context): SportTypesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext ?: context
                    SportTypesRepository(appContext).also { INSTANCE = it }
                }
            }
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