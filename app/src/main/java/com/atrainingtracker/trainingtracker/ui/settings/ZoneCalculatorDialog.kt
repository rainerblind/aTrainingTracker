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

package com.atrainingtracker.trainingtracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType
import com.atrainingtracker.trainingtracker.zones.ValidationResult
import com.atrainingtracker.trainingtracker.zones.ZoneCalculationMath
import com.atrainingtracker.trainingtracker.zones.ZoneNumberParser
import com.atrainingtracker.trainingtracker.zones.ZoneValidator

enum class HrMethod {
    MAX_HR,
    KARVONEN
}

data class ZoneCalculatorUiState(
    val zoneType: ZoneType,
    val isCustomized: Boolean,
    val ftpInput: String = "240",
    val ftpErrorResId: Int? = null,
    val hrMaxInput: String = "185",
    val hrRestInput: String = "55",
    val hrMethod: HrMethod = HrMethod.KARVONEN,
    val hrMaxErrorResId: Int? = null,
    val hrRestErrorResId: Int? = null,
    val hrGeneralErrorResId: Int? = null,
    val calculatedZones: List<Int>? = null,
    val showOverwriteWarning: Boolean = false,
    val isSaving: Boolean = false
) {
    val isValid: Boolean
        get() = calculatedZones != null &&
                (if (zoneType == ZoneType.PWR_BIKE) ftpErrorResId == null
                else hrMaxErrorResId == null && (hrMethod != HrMethod.KARVONEN || hrRestErrorResId == null) && hrGeneralErrorResId == null)

    val canApply: Boolean
        get() = isValid && !isSaving
}

class ZoneCalculatorStateHolder(
    val zoneType: ZoneType,
    initialCustomized: Boolean,
    initialFtp: Int? = null,
    initialHrMax: Int? = null,
    initialHrRest: Int? = null,
    initialHrMethod: HrMethod = HrMethod.KARVONEN
) {
    var state: ZoneCalculatorUiState by mutableStateOf(
        ZoneCalculatorUiState(
            zoneType = zoneType,
            isCustomized = initialCustomized,
            ftpInput = (initialFtp?.takeIf { it in ZoneCalculationMath.MIN_FTP..ZoneCalculationMath.MAX_FTP } ?: SettingsDataStore.DEFAULT_CYCLING_FTP).toString(),
            hrMaxInput = (initialHrMax?.takeIf { it in ZoneCalculationMath.MIN_HR_MAX..ZoneCalculationMath.MAX_HR_MAX } ?: SettingsDataStore.DEFAULT_HR_MAX).toString(),
            hrRestInput = (initialHrRest?.takeIf { it in ZoneCalculationMath.MIN_HR_REST..ZoneCalculationMath.MAX_HR_REST } ?: SettingsDataStore.DEFAULT_HR_REST).toString(),
            hrMethod = initialHrMethod
        )
    )
        private set

    init {
        recalculate()
    }

    fun onFtpChanged(input: String) {
        state = state.copy(ftpInput = input)
        recalculate()
    }

    fun onHrMaxChanged(input: String) {
        state = state.copy(hrMaxInput = input)
        recalculate()
    }

    fun onHrRestChanged(input: String) {
        state = state.copy(hrRestInput = input)
        recalculate()
    }

    fun onHrMethodChanged(method: HrMethod) {
        state = state.copy(hrMethod = method)
        recalculate()
    }

    fun onApplyClicked(onProceedToSave: (List<Int>) -> Unit) {
        if (!state.canApply) return
        val zones = state.calculatedZones ?: return
        if (state.isCustomized) {
            state = state.copy(showOverwriteWarning = true)
        } else {
            state = state.copy(isSaving = true)
            onProceedToSave(zones)
        }
    }

    fun onConfirmOverwrite(onProceedToSave: (List<Int>) -> Unit) {
        state = state.copy(showOverwriteWarning = false, isSaving = true)
        val zones = state.calculatedZones ?: return
        onProceedToSave(zones)
    }

    fun onDismissOverwriteWarning() {
        state = state.copy(showOverwriteWarning = false)
    }

    fun onSaveCompleted() {
        state = state.copy(isSaving = false, isCustomized = false)
    }

    fun onSaveFailed() {
        state = state.copy(isSaving = false)
    }

    private fun recalculate() {
        if (zoneType == ZoneType.PWR_BIKE) {
            val ftp = ZoneNumberParser.parseIntOrNull(state.ftpInput)
            if (ftp == null) {
                state = state.copy(
                    ftpErrorResId = R.string.zone_calc_error_ftp_range,
                    calculatedZones = null
                )
            } else {
                when (val validation = ZoneValidator.validateFtp(ftp)) {
                    is ValidationResult.Invalid -> {
                        state = state.copy(
                            ftpErrorResId = validation.errorResId,
                            calculatedZones = null
                        )
                    }
                    is ValidationResult.Valid -> {
                        val t = ZoneCalculationMath.calculateCogganPowerZones(ftp)
                        val zones = listOf(t.z1Max, t.z2Max, t.z3Max, t.z4Max, t.z5Max, t.z6Max)
                        state = state.copy(
                            ftpErrorResId = null,
                            calculatedZones = zones
                        )
                    }
                }
            }
        } else {
            val hrMax = ZoneNumberParser.parseIntOrNull(state.hrMaxInput)
            val hrRest = ZoneNumberParser.parseIntOrNull(state.hrRestInput)

            var hrMaxError: Int? = null
            var hrRestError: Int? = null
            var hrGeneralError: Int? = null

            if (hrMax == null || hrMax !in ZoneCalculationMath.MIN_HR_MAX..ZoneCalculationMath.MAX_HR_MAX) {
                hrMaxError = R.string.zone_calc_error_hr_max_range
            }

            if (state.hrMethod == HrMethod.KARVONEN) {
                if (hrRest == null || hrRest !in ZoneCalculationMath.MIN_HR_REST..ZoneCalculationMath.MAX_HR_REST) {
                    hrRestError = R.string.zone_calc_error_hr_rest_range
                }
                if (hrMax != null && hrRest != null) {
                    if (hrMax <= hrRest) {
                        hrGeneralError = R.string.zone_calc_error_hr_max_greater_rest
                    } else if (hrMax - hrRest < ZoneCalculationMath.MIN_KARVONEN_RESERVE) {
                        hrGeneralError = R.string.zone_calc_error_karvonen_delta
                    }
                }
            }

            val hasError = hrMaxError != null || (state.hrMethod == HrMethod.KARVONEN && hrRestError != null) || hrGeneralError != null
            val zones = if (!hasError && hrMax != null) {
                if (state.hrMethod == HrMethod.KARVONEN && hrRest != null) {
                    val t = ZoneCalculationMath.calculateKarvonenHeartRateZones(hrMax, hrRest)
                    listOf(t.z1Max, t.z2Max, t.z3Max, t.z4Max)
                } else {
                    val t = ZoneCalculationMath.calculateHeartRateZonesMax(hrMax)
                    listOf(t.z1Max, t.z2Max, t.z3Max, t.z4Max)
                }
            } else {
                null
            }

            state = state.copy(
                hrMaxErrorResId = hrMaxError,
                hrRestErrorResId = hrRestError,
                hrGeneralErrorResId = hrGeneralError,
                calculatedZones = zones
            )
        }
    }
}

@Composable
fun ZoneCalculatorDialog(
    holder: ZoneCalculatorStateHolder,
    onDismiss: () -> Unit,
    onSavePower: (ftp: Int, cutoffs: List<Int>) -> Unit,
    onSaveHeartRate: (zoneType: ZoneType, hrMax: Int, hrRest: Int?, method: String, cutoffs: List<Int>) -> Unit
) {
    val state = holder.state

    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = {
            Text(
                text = stringResource(
                    if (state.zoneType == ZoneType.PWR_BIKE) R.string.zone_calc_dialog_title_power
                    else R.string.zone_calc_dialog_title_hr
                ),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.zoneType == ZoneType.PWR_BIKE) {
                    OutlinedTextField(
                        value = state.ftpInput,
                        onValueChange = { holder.onFtpChanged(it) },
                        label = { Text(stringResource(R.string.zone_calc_ftp_label)) },
                        suffix = { Text("W") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.ftpErrorResId != null,
                        supportingText = {
                            if (state.ftpErrorResId != null) {
                                Text(
                                    text = stringResource(
                                        state.ftpErrorResId,
                                        ZoneCalculationMath.MIN_FTP,
                                        ZoneCalculationMath.MAX_FTP
                                    ),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // HR Method Selection
                    Text(
                        text = stringResource(R.string.zone_calc_method_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.hrMethod == HrMethod.KARVONEN,
                            onClick = { holder.onHrMethodChanged(HrMethod.KARVONEN) },
                            label = { Text(stringResource(R.string.zone_calc_method_karvonen)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = state.hrMethod == HrMethod.MAX_HR,
                            onClick = { holder.onHrMethodChanged(HrMethod.MAX_HR) },
                            label = { Text(stringResource(R.string.zone_calc_method_max_hr)) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // HRmax Field
                    OutlinedTextField(
                        value = state.hrMaxInput,
                        onValueChange = { holder.onHrMaxChanged(it) },
                        label = { Text(stringResource(R.string.zone_calc_hr_max_label)) },
                        suffix = { Text("bpm") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.hrMaxErrorResId != null,
                        supportingText = {
                            if (state.hrMaxErrorResId != null) {
                                Text(
                                    text = stringResource(
                                        state.hrMaxErrorResId,
                                        ZoneCalculationMath.MIN_HR_MAX,
                                        ZoneCalculationMath.MAX_HR_MAX
                                    ),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // HRrest Field (if Karvonen)
                    if (state.hrMethod == HrMethod.KARVONEN) {
                        OutlinedTextField(
                            value = state.hrRestInput,
                            onValueChange = { holder.onHrRestChanged(it) },
                            label = { Text(stringResource(R.string.zone_calc_hr_rest_label)) },
                            suffix = { Text("bpm") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = state.hrRestErrorResId != null,
                            supportingText = {
                                if (state.hrRestErrorResId != null) {
                                    Text(
                                        text = stringResource(
                                            state.hrRestErrorResId,
                                            ZoneCalculationMath.MIN_HR_REST,
                                            ZoneCalculationMath.MAX_HR_REST
                                        ),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // General Invariant Error Message
                    if (state.hrGeneralErrorResId != null) {
                        Text(
                            text = stringResource(state.hrGeneralErrorResId),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Live Zone Preview Card
                val zones = state.calculatedZones
                if (zones != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.zone_calc_preview_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            val zoneColors = if (state.zoneType == ZoneType.PWR_BIKE) {
                                listOf(
                                    colorResource(R.color.zone_1),
                                    colorResource(R.color.zone_2),
                                    colorResource(R.color.zone_3),
                                    colorResource(R.color.zone_4),
                                    colorResource(R.color.zone_5),
                                    colorResource(R.color.zone_6),
                                    colorResource(R.color.zone_7)
                                )
                            } else {
                                listOf(
                                    colorResource(R.color.zone_1),
                                    colorResource(R.color.zone_2),
                                    colorResource(R.color.zone_3),
                                    colorResource(R.color.zone_4),
                                    colorResource(R.color.zone_5)
                                )
                            }

                            val unit = if (state.zoneType == ZoneType.PWR_BIKE) "W" else "bpm"

                            if (state.zoneType == ZoneType.PWR_BIKE && zones.size == 6) {
                                PreviewZoneRow("Z1", "<= ${zones[0]} $unit", zoneColors[0])
                                PreviewZoneRow("Z2", "<= ${zones[1]} $unit", zoneColors[1])
                                PreviewZoneRow("Z3", "<= ${zones[2]} $unit", zoneColors[2])
                                PreviewZoneRow("Z4", "<= ${zones[3]} $unit", zoneColors[3])
                                PreviewZoneRow("Z5", "<= ${zones[4]} $unit", zoneColors[4])
                                PreviewZoneRow("Z6", "<= ${zones[5]} $unit", zoneColors[5])
                                PreviewZoneRow("Z7", "> ${zones[5]} $unit (max 2500 $unit)", zoneColors[6])
                            } else if (zones.size == 4) {
                                PreviewZoneRow("Z1", "<= ${zones[0]} $unit", zoneColors[0])
                                PreviewZoneRow("Z2", "<= ${zones[1]} $unit", zoneColors[1])
                                PreviewZoneRow("Z3", "<= ${zones[2]} $unit", zoneColors[2])
                                PreviewZoneRow("Z4", "<= ${zones[3]} $unit", zoneColors[3])
                                PreviewZoneRow("Z5", "> ${zones[3]} $unit", zoneColors[4])
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    holder.onApplyClicked { cutoffs ->
                        if (state.zoneType == ZoneType.PWR_BIKE) {
                            val ftp = ZoneNumberParser.parseIntOrNull(state.ftpInput) ?: SettingsDataStore.DEFAULT_CYCLING_FTP
                            onSavePower(ftp, cutoffs)
                        } else {
                            val hrMax = ZoneNumberParser.parseIntOrNull(state.hrMaxInput) ?: SettingsDataStore.DEFAULT_HR_MAX
                            val hrRest = if (state.hrMethod == HrMethod.KARVONEN) ZoneNumberParser.parseIntOrNull(state.hrRestInput) else null
                            val method = if (state.hrMethod == HrMethod.KARVONEN) "KARVONEN" else "MAX_HR"
                            onSaveHeartRate(state.zoneType, hrMax, hrRest, method, cutoffs)
                        }
                    }
                },
                enabled = state.canApply
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.zone_calc_apply_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isSaving
            ) {
                Text(stringResource(R.string.zone_calc_cancel_button))
            }
        }
    )

    // Overwrite Confirmation Modal
    if (state.showOverwriteWarning) {
        val benchmarkValue = if (state.zoneType == ZoneType.PWR_BIKE) {
            ZoneNumberParser.parseIntOrNull(state.ftpInput) ?: SettingsDataStore.DEFAULT_CYCLING_FTP
        } else {
            ZoneNumberParser.parseIntOrNull(state.hrMaxInput) ?: SettingsDataStore.DEFAULT_HR_MAX
        }

        AlertDialog(
            onDismissRequest = { holder.onDismissOverwriteWarning() },
            title = {
                Text(stringResource(R.string.zone_calc_overwrite_warning_title))
            },
            text = {
                Text(stringResource(R.string.zone_calc_overwrite_warning_message, benchmarkValue))
            },
            confirmButton = {
                Button(
                    onClick = {
                        holder.onConfirmOverwrite { cutoffs ->
                            if (state.zoneType == ZoneType.PWR_BIKE) {
                                val ftp = ZoneNumberParser.parseIntOrNull(state.ftpInput) ?: SettingsDataStore.DEFAULT_CYCLING_FTP
                                onSavePower(ftp, cutoffs)
                            } else {
                                val hrMax = ZoneNumberParser.parseIntOrNull(state.hrMaxInput) ?: SettingsDataStore.DEFAULT_HR_MAX
                                val hrRest = if (state.hrMethod == HrMethod.KARVONEN) ZoneNumberParser.parseIntOrNull(state.hrRestInput) else null
                                val method = if (state.hrMethod == HrMethod.KARVONEN) "KARVONEN" else "MAX_HR"
                                onSaveHeartRate(state.zoneType, hrMax, hrRest, method, cutoffs)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.zone_calc_apply_button))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { holder.onDismissOverwriteWarning() }
                ) {
                    Text(stringResource(R.string.zone_calc_cancel_button))
                }
            }
        )
    }
}

@Composable
private fun PreviewZoneRow(zone: String, cutoffText: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = zone,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = cutoffText,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
