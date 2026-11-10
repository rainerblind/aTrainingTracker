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

package com.atrainingtracker.trainingtracker.ui.common.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

/**
 * Standardized numeric interval filter section across filter bottom sheets (REQ-UI-509.2).
 *
 * Displays:
 * 1. Section title in [MaterialTheme.typography.titleMedium] tinted primary.
 * 2. Athletic preset chips in a [FlowRow].
 * 3. Min/Max text input fields with unit indicator and decoupled text buffers.
 *
 * Preserves detached text buffers ([minText], [maxText]) to prevent intermediate typing collapse (e.g. "1.").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NumericIntervalFilterSection(
    title: String,
    unit: String,
    presets: List<Triple<String, Double?, Double?>>,
    minMeters: Double?,
    maxMeters: Double?,
    minText: String,
    maxText: String,
    multiplier: Double,
    onBoundsChange: (minMeters: Double?, maxMeters: Double?, minText: String, maxText: String) -> Unit,
    formatInputValue: (Double?, Double) -> String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            presets.forEach { (label, minM, maxM) ->
                val isSelected = minMeters == minM && maxMeters == maxM
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            onBoundsChange(null, null, "", "")
                        } else {
                            val newMinText = formatInputValue(minM, multiplier)
                            val newMaxText = formatInputValue(maxM, multiplier)
                            onBoundsChange(minM, maxM, newMinText, newMaxText)
                        }
                    },
                    label = { Text(label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = minText,
                onValueChange = { str ->
                    val parsed = str.toDoubleOrNull()?.times(multiplier)
                    onBoundsChange(parsed, maxMeters, str, maxText)
                },
                label = { Text("${stringResource(R.string.filter_min_label)} ($unit)") },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = maxText,
                onValueChange = { str ->
                    val parsed = str.toDoubleOrNull()?.times(multiplier)
                    onBoundsChange(minMeters, parsed, minText, str)
                },
                label = { Text("${stringResource(R.string.filter_max_label)} ($unit)") },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
