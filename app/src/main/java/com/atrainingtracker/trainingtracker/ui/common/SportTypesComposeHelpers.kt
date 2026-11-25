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

package com.atrainingtracker.trainingtracker.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.repositories.SimpleSportTypeInfo
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository

/**
 * Observes [SportTypesRepository.sportTypesChanged] so Compose sport-type pickers recompute
 * immediately after Swimming activation or any external `SportType.db` mutation (REQ-SET-134.3).
 */
@Composable
fun rememberSportTypesList(bSportType: BSportType? = BSportType.UNKNOWN): List<SimpleSportTypeInfo> {
    val context = LocalContext.current
    val revision by produceState(initialValue = 0) {
        SportTypesRepository.sportTypesChanged.collect {
            value++
        }
    }
    return remember(context, bSportType, revision) {
        SportTypesRepository.getInstance(context).getSportTypes(bSportType)
    }
}
