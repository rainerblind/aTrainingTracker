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

package com.atrainingtracker.trainingtracker.ui.settings.tuning.categories

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.clusters.ClusterTuningContent

/**
 * Workout route clustering tuning category composable (REQ-SET-075, Rule 36).
 * Single Source of Truth for algorithmic route matching preferences embedded in Advanced Settings.
 */
@Composable
fun ClusterTuningSection(
    endpointTolerance: Float,
    onEndpointToleranceChange: (Float) -> Unit,
    apexTolerance: Float,
    onApexToleranceChange: (Float) -> Unit,
    distanceTolerance: Float,
    onDistanceToleranceChange: (Float) -> Unit,
    altitudePositionTolerance: Float,
    onAltitudePositionToleranceChange: (Float) -> Unit,
    useSportTypeForClustering: Boolean,
    onUseSportTypeChange: (Boolean) -> Unit,
    useAltitudePosForClustering: Boolean,
    onUseAltitudePosChange: (Boolean) -> Unit,
    onValueChangeFinished: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ClusterTuningContent(
            endpointTolerance = endpointTolerance,
            onEndpointToleranceChange = onEndpointToleranceChange,
            apexTolerance = apexTolerance,
            onApexToleranceChange = onApexToleranceChange,
            distanceTolerance = distanceTolerance,
            onDistanceToleranceChange = onDistanceToleranceChange,
            altitudePositionTolerance = altitudePositionTolerance,
            onAltitudePositionToleranceChange = onAltitudePositionToleranceChange,
            useSportTypeForClustering = useSportTypeForClustering,
            onUseSportTypeChange = onUseSportTypeChange,
            useAltitudePosForClustering = useAltitudePosForClustering,
            onUseAltitudePosChange = onUseAltitudePosChange,
            onValueChangeFinished = onValueChangeFinished,
            isDialog = false
        )
    }
}
