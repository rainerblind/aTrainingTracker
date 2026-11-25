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

package com.atrainingtracker.trainingtracker.ui.settings.zones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.zones.ValidationResult
import com.atrainingtracker.trainingtracker.zones.ZoneNumberParser
import com.atrainingtracker.trainingtracker.zones.ZoneValidator
import java.util.Locale
import kotlin.math.abs

/**
 * Snapshot of current threshold values rendered inside [PhysiologicalThresholdsCard] (`REQ-SET-136.1`).
 */
data class ZoneThresholdsValues(
    val ftp: Int? = null,
    val cp: Int? = null,
    val wPrimeKj: Float? = null,
    val lt1PwrBike: Int? = null,
    val hrMax: Int? = null,
    val hrRest: Int? = null,
    val lthr: Int? = null,
    val lt1Hr: Int? = null
)

/**
 * Pure Expert-Settings gating helper resolving which physiological threshold inputs are visible
 * for a given [zoneType] and [tuningConfig] (`REQ-SET-136.1`, `TST-SET-136.1`).
 */
fun resolveVisibleThresholdFields(
    zoneType: ZoneType,
    tuningConfig: TuningConfig
): List<AthleteMetricType> = when (zoneType) {
    ZoneType.PWR_BIKE -> buildList {
        if (tuningConfig.enableCogganPowerMetrics) add(AthleteMetricType.FTP_WATTS)
        if (tuningConfig.enableCriticalPowerModel) {
            add(AthleteMetricType.CP_WATTS)
            add(AthleteMetricType.W_PRIME_KJ)
        }
        if (tuningConfig.enableAerobicEfficiencyModel) add(AthleteMetricType.LT1_PWR_BIKE_WATTS)
    }
    ZoneType.HR_RUN -> buildList {
        add(AthleteMetricType.HR_MAX_RUN_BPM)
        add(AthleteMetricType.HR_REST_BPM)
        if (tuningConfig.enablePmcModel || tuningConfig.enableAerobicEfficiencyModel) {
            add(AthleteMetricType.LTHR_RUN_BPM)
        }
        if (tuningConfig.enableAerobicEfficiencyModel) {
            add(AthleteMetricType.LT1_HR_RUN_BPM)
        }
    }
    ZoneType.HR_BIKE -> buildList {
        add(AthleteMetricType.HR_MAX_BIKE_BPM)
        add(AthleteMetricType.HR_REST_BPM)
        if (tuningConfig.enablePmcModel || tuningConfig.enableAerobicEfficiencyModel) {
            add(AthleteMetricType.LTHR_BIKE_BPM)
        }
        if (tuningConfig.enableAerobicEfficiencyModel) {
            add(AthleteMetricType.LT1_HR_BIKE_BPM)
        }
    }
}

/**
 * Unified Expert-gated **Physiological Thresholds** card embedded at the top of each Zones tab
 * (`HR Run`, `HR Bike`, `Power Bike`) (`REQ-SET-136.1`, `REQ-SET-136.2`, `REQ-SET-136.3`).
 *
 * Editing any threshold validates bounds and cross-field physiological invariants
 * (`HRrest < LT1 < LTHR < HRmax`, `LT1_W < FTP`) against canonical [AthleteMetricType] bounds
 * and persists via [onSaveThreshold] without silently overwriting manual `Z1..Zn` zone cutoffs.
 * Pending edits blocked temporarily by a cross-field invariant are flushed together as soon as
 * the invariant recovers.
 */
@Composable
fun PhysiologicalThresholdsCard(
    zoneType: ZoneType,
    tuningConfig: TuningConfig,
    values: ZoneThresholdsValues,
    onSaveThreshold: (AthleteMetricType, Double) -> Unit,
    onSaveCpAndWPrime: (Int, Double) -> Unit,
    onOpenRecalculateDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleFields = remember(zoneType, tuningConfig) {
        resolveVisibleThresholdFields(zoneType, tuningConfig)
    }
    if (visibleFields.isEmpty()) return

    var ftpText by remember(zoneType) { mutableStateOf(values.ftp?.toString() ?: "") }
    var cpText by remember(zoneType) { mutableStateOf(values.cp?.toString() ?: "") }
    var wPrimeText by remember(zoneType) {
        mutableStateOf(values.wPrimeKj?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "")
    }
    var lt1PwrText by remember(zoneType) { mutableStateOf(values.lt1PwrBike?.toString() ?: "") }
    var hrMaxText by remember(zoneType) { mutableStateOf(values.hrMax?.toString() ?: "") }
    var hrRestText by remember(zoneType) { mutableStateOf(values.hrRest?.toString() ?: "") }
    var lthrText by remember(zoneType) { mutableStateOf(values.lthr?.toString() ?: "") }
    var lt1HrText by remember(zoneType) { mutableStateOf(values.lt1Hr?.toString() ?: "") }
    var showCpEstimatorDialog by remember { mutableStateOf(false) }

    // Tracks dirty individually-valid edits awaiting cross-field invariant recovery (plain map, zero composition writes)
    val pendingEdits = remember(zoneType) { LinkedHashMap<AthleteMetricType, Double>() }

    val hrMaxMetricType = if (zoneType == ZoneType.HR_RUN) AthleteMetricType.HR_MAX_RUN_BPM else AthleteMetricType.HR_MAX_BIKE_BPM
    val lthrMetricType = if (zoneType == ZoneType.HR_RUN) AthleteMetricType.LTHR_RUN_BPM else AthleteMetricType.LTHR_BIKE_BPM
    val lt1HrMetricType = if (zoneType == ZoneType.HR_RUN) AthleteMetricType.LT1_HR_RUN_BPM else AthleteMetricType.LT1_HR_BIKE_BPM

    // Synchronize external updates (e.g., Aftermath HRV acceptance, Estimator, or cross-tab HRrest)
    // without clobbering un-flushed user edits waiting for cross-field recovery.
    LaunchedEffect(values.ftp) {
        if (AthleteMetricType.FTP_WATTS !in pendingEdits && values.ftp != null && ZoneNumberParser.parseIntOrNull(ftpText) != values.ftp) {
            ftpText = values.ftp.toString()
        }
    }
    LaunchedEffect(values.cp) {
        if (AthleteMetricType.CP_WATTS !in pendingEdits && values.cp != null && ZoneNumberParser.parseIntOrNull(cpText) != values.cp) {
            cpText = values.cp.toString()
        }
    }
    LaunchedEffect(values.wPrimeKj) {
        val currentParsed = wPrimeText.trim().replace(',', '.').toDoubleOrNull()
        if (AthleteMetricType.W_PRIME_KJ !in pendingEdits && values.wPrimeKj != null && (currentParsed == null || abs(currentParsed - values.wPrimeKj) > 0.05)) {
            wPrimeText = String.format(Locale.getDefault(), "%.1f", values.wPrimeKj)
        }
    }
    LaunchedEffect(values.lt1PwrBike) {
        if (AthleteMetricType.LT1_PWR_BIKE_WATTS !in pendingEdits && values.lt1PwrBike != null && ZoneNumberParser.parseIntOrNull(lt1PwrText) != values.lt1PwrBike) {
            lt1PwrText = values.lt1PwrBike.toString()
        }
    }
    LaunchedEffect(values.hrMax) {
        if (hrMaxMetricType !in pendingEdits && values.hrMax != null && ZoneNumberParser.parseIntOrNull(hrMaxText) != values.hrMax) {
            hrMaxText = values.hrMax.toString()
        }
    }
    LaunchedEffect(values.hrRest) {
        if (AthleteMetricType.HR_REST_BPM !in pendingEdits && values.hrRest != null && ZoneNumberParser.parseIntOrNull(hrRestText) != values.hrRest) {
            hrRestText = values.hrRest.toString()
        }
    }
    LaunchedEffect(values.lthr) {
        if (lthrMetricType !in pendingEdits && values.lthr != null && ZoneNumberParser.parseIntOrNull(lthrText) != values.lthr) {
            lthrText = values.lthr.toString()
        }
    }
    LaunchedEffect(values.lt1Hr) {
        if (lt1HrMetricType !in pendingEdits && values.lt1Hr != null && ZoneNumberParser.parseIntOrNull(lt1HrText) != values.lt1Hr) {
            lt1HrText = values.lt1Hr.toString()
        }
    }

    fun isFieldTextInvalid(type: AthleteMetricType, text: String): Boolean {
        if (text.isBlank()) return false
        val parsed = text.trim().replace(',', '.').toDoubleOrNull() ?: return true
        return !type.isValidMetricValue(parsed)
    }

    val parsedFtp = ZoneNumberParser.parseIntOrNull(ftpText)
    val parsedCp = ZoneNumberParser.parseIntOrNull(cpText)
    val parsedWPrime = wPrimeText.trim().replace(',', '.').toDoubleOrNull()
    val parsedLt1Pwr = ZoneNumberParser.parseIntOrNull(lt1PwrText)
    val parsedHrMax = ZoneNumberParser.parseIntOrNull(hrMaxText)
    val parsedHrRest = ZoneNumberParser.parseIntOrNull(hrRestText)
    val parsedLthr = ZoneNumberParser.parseIntOrNull(lthrText)
    val parsedLt1Hr = ZoneNumberParser.parseIntOrNull(lt1HrText)

    val crossFieldErrorResId: Int? = if (zoneType == ZoneType.PWR_BIKE) {
        (ZoneValidator.validatePowerThresholds(
            ftp = parsedFtp,
            cp = parsedCp,
            wPrimeKj = parsedWPrime,
            lt1Power = parsedLt1Pwr
        ) as? ValidationResult.Invalid)?.errorResId
    } else {
        (ZoneValidator.validateHeartRateThresholds(
            hrMax = parsedHrMax,
            hrRest = parsedHrRest,
            lthr = parsedLthr,
            lt1Hr = parsedLt1Hr
        ) as? ValidationResult.Invalid)?.errorResId
    }

    fun tryFlushPowerEdits(
        nextFtpText: String = ftpText,
        nextCpText: String = cpText,
        nextWPrimeText: String = wPrimeText,
        nextLt1PwrText: String = lt1PwrText
    ) {
        val f = ZoneNumberParser.parseIntOrNull(nextFtpText)
        val c = ZoneNumberParser.parseIntOrNull(nextCpText)
        val w = nextWPrimeText.trim().replace(',', '.').toDoubleOrNull()
        val l1 = ZoneNumberParser.parseIntOrNull(nextLt1PwrText)
        val hasParseError = isFieldTextInvalid(AthleteMetricType.FTP_WATTS, nextFtpText) ||
            isFieldTextInvalid(AthleteMetricType.CP_WATTS, nextCpText) ||
            isFieldTextInvalid(AthleteMetricType.W_PRIME_KJ, nextWPrimeText) ||
            isFieldTextInvalid(AthleteMetricType.LT1_PWR_BIKE_WATTS, nextLt1PwrText)
        if (!hasParseError && ZoneValidator.validatePowerThresholds(f, c, w, l1) is ValidationResult.Valid) {
            val toCommit = pendingEdits.entries.toList()
            pendingEdits.clear()
            toCommit.forEach { (m, v) -> onSaveThreshold(m, v) }
        }
    }

    fun tryFlushHrEdits(
        nextHrMaxText: String = hrMaxText,
        nextHrRestText: String = hrRestText,
        nextLthrText: String = lthrText,
        nextLt1HrText: String = lt1HrText
    ) {
        val hMax = ZoneNumberParser.parseIntOrNull(nextHrMaxText)
        val hRest = ZoneNumberParser.parseIntOrNull(nextHrRestText)
        val l2 = ZoneNumberParser.parseIntOrNull(nextLthrText)
        val l1 = ZoneNumberParser.parseIntOrNull(nextLt1HrText)
        val hasParseError = isFieldTextInvalid(hrMaxMetricType, nextHrMaxText) ||
            isFieldTextInvalid(AthleteMetricType.HR_REST_BPM, nextHrRestText) ||
            isFieldTextInvalid(lthrMetricType, nextLthrText) ||
            isFieldTextInvalid(lt1HrMetricType, nextLt1HrText)
        if (!hasParseError && ZoneValidator.validateHeartRateThresholds(hMax, hRest, l2, l1) is ValidationResult.Valid) {
            val toCommit = pendingEdits.entries.toList()
            pendingEdits.clear()
            toCommit.forEach { (m, v) -> onSaveThreshold(m, v) }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("PhysiologicalThresholdsCard_${zoneType.name}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.zone_thresholds_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.zone_thresholds_card_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            visibleFields.chunked(2).forEach { rowFields ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowFields.forEach { metricType ->
                        val (textVal, onTextChange) = when (metricType) {
                            AthleteMetricType.FTP_WATTS -> ftpText to { raw: String ->
                                ftpText = raw
                                val v = ZoneNumberParser.parseIntOrNull(raw)?.toDouble()
                                if (v != null && metricType.isValidMetricValue(v)) {
                                    pendingEdits[metricType] = v
                                } else {
                                    pendingEdits.remove(metricType)
                                }
                                tryFlushPowerEdits(nextFtpText = raw)
                            }
                            AthleteMetricType.CP_WATTS -> cpText to { raw: String ->
                                cpText = raw
                                val v = ZoneNumberParser.parseIntOrNull(raw)?.toDouble()
                                if (v != null && metricType.isValidMetricValue(v)) {
                                    pendingEdits[metricType] = v
                                } else {
                                    pendingEdits.remove(metricType)
                                }
                                tryFlushPowerEdits(nextCpText = raw)
                            }
                            AthleteMetricType.W_PRIME_KJ -> wPrimeText to { raw: String ->
                                wPrimeText = raw
                                val v = raw.trim().replace(',', '.').toDoubleOrNull()
                                if (v != null && metricType.isValidMetricValue(v)) {
                                    pendingEdits[metricType] = v
                                } else {
                                    pendingEdits.remove(metricType)
                                }
                                tryFlushPowerEdits(nextWPrimeText = raw)
                            }
                            AthleteMetricType.LT1_PWR_BIKE_WATTS -> lt1PwrText to { raw: String ->
                                lt1PwrText = raw
                                val v = ZoneNumberParser.parseIntOrNull(raw)?.toDouble()
                                if (v != null && metricType.isValidMetricValue(v)) {
                                    pendingEdits[metricType] = v
                                } else {
                                    pendingEdits.remove(metricType)
                                }
                                tryFlushPowerEdits(nextLt1PwrText = raw)
                            }
                            AthleteMetricType.HR_MAX_RUN_BPM,
                            AthleteMetricType.HR_MAX_BIKE_BPM -> hrMaxText to { raw: String ->
                                hrMaxText = raw
                                val v = ZoneNumberParser.parseIntOrNull(raw)?.toDouble()
                                if (v != null && metricType.isValidMetricValue(v)) {
                                    pendingEdits[metricType] = v
                                } else {
                                    pendingEdits.remove(metricType)
                                }
                                tryFlushHrEdits(nextHrMaxText = raw)
                            }
                            AthleteMetricType.HR_REST_BPM -> hrRestText to { raw: String ->
                                hrRestText = raw
                                val v = ZoneNumberParser.parseIntOrNull(raw)?.toDouble()
                                if (v != null && metricType.isValidMetricValue(v)) {
                                    pendingEdits[metricType] = v
                                } else {
                                    pendingEdits.remove(metricType)
                                }
                                tryFlushHrEdits(nextHrRestText = raw)
                            }
                            AthleteMetricType.LTHR_RUN_BPM,
                            AthleteMetricType.LTHR_BIKE_BPM -> lthrText to { raw: String ->
                                lthrText = raw
                                val v = ZoneNumberParser.parseIntOrNull(raw)?.toDouble()
                                if (v != null && metricType.isValidMetricValue(v)) {
                                    pendingEdits[metricType] = v
                                } else {
                                    pendingEdits.remove(metricType)
                                }
                                tryFlushHrEdits(nextLthrText = raw)
                            }
                            AthleteMetricType.LT1_HR_RUN_BPM,
                            AthleteMetricType.LT1_HR_BIKE_BPM -> lt1HrText to { raw: String ->
                                lt1HrText = raw
                                val v = ZoneNumberParser.parseIntOrNull(raw)?.toDouble()
                                if (v != null && metricType.isValidMetricValue(v)) {
                                    pendingEdits[metricType] = v
                                } else {
                                    pendingEdits.remove(metricType)
                                }
                                tryFlushHrEdits(nextLt1HrText = raw)
                            }
                            AthleteMetricType.WEIGHT_KG -> "" to { _: String -> }
                        }

                        val fieldInvalid = isFieldTextInvalid(metricType, textVal)
                        ThresholdNumericField(
                            metricType = metricType,
                            value = textVal,
                            onValueChange = onTextChange,
                            isError = fieldInvalid || (crossFieldErrorResId != null && textVal.isNotBlank()),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowFields.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            if (crossFieldErrorResId != null) {
                val errorText = when (crossFieldErrorResId) {
                    R.string.zone_calc_error_ftp_range -> stringResource(
                        crossFieldErrorResId,
                        AthleteMetricType.FTP_WATTS.minMetricValue.toInt(),
                        AthleteMetricType.FTP_WATTS.maxMetricValue.toInt()
                    )
                    R.string.zone_thresholds_error_cp_range -> stringResource(
                        crossFieldErrorResId,
                        AthleteMetricType.CP_WATTS.minMetricValue.toInt(),
                        AthleteMetricType.CP_WATTS.maxMetricValue.toInt()
                    )
                    R.string.zone_thresholds_error_lt1_pwr_range -> stringResource(
                        crossFieldErrorResId,
                        AthleteMetricType.LT1_PWR_BIKE_WATTS.minMetricValue.toInt(),
                        AthleteMetricType.LT1_PWR_BIKE_WATTS.maxMetricValue.toInt()
                    )
                    R.string.zone_calc_error_hr_max_range -> stringResource(
                        crossFieldErrorResId,
                        AthleteMetricType.HR_MAX_RUN_BPM.minMetricValue.toInt(),
                        AthleteMetricType.HR_MAX_RUN_BPM.maxMetricValue.toInt()
                    )
                    R.string.zone_calc_error_hr_rest_range -> stringResource(
                        crossFieldErrorResId,
                        AthleteMetricType.HR_REST_BPM.minMetricValue.toInt(),
                        AthleteMetricType.HR_REST_BPM.maxMetricValue.toInt()
                    )
                    R.string.zone_calc_error_lthr_range -> stringResource(
                        crossFieldErrorResId,
                        AthleteMetricType.LTHR_RUN_BPM.minMetricValue.toInt(),
                        AthleteMetricType.LTHR_RUN_BPM.maxMetricValue.toInt()
                    )
                    R.string.zone_calc_error_lt1_hr_range -> stringResource(
                        crossFieldErrorResId,
                        AthleteMetricType.LT1_HR_RUN_BPM.minMetricValue.toInt(),
                        AthleteMetricType.LT1_HR_RUN_BPM.maxMetricValue.toInt()
                    )
                    else -> stringResource(crossFieldErrorResId)
                }
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("threshold_validation_error")
                )
            }

            if (zoneType == ZoneType.PWR_BIKE && tuningConfig.enableCriticalPowerModel) {
                OutlinedButton(
                    onClick = { showCpEstimatorDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_cp_estimator_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.zone_thresholds_open_cp_estimator),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            FilledTonalButton(
                onClick = onOpenRecalculateDialog,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recalculate_zones_cta")
            ) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.zone_thresholds_recalculate_cta),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showCpEstimatorDialog) {
        CpWPrimeEstimatorDialog(
            onDismiss = { showCpEstimatorDialog = false },
            onApply = { estCp, estWPrime ->
                pendingEdits.remove(AthleteMetricType.CP_WATTS)
                pendingEdits.remove(AthleteMetricType.W_PRIME_KJ)
                cpText = estCp.toString()
                wPrimeText = String.format(Locale.getDefault(), "%.1f", estWPrime)
                onSaveCpAndWPrime(estCp, estWPrime)
                showCpEstimatorDialog = false
            }
        )
    }
}

@Composable
private fun ThresholdNumericField(
    metricType: AthleteMetricType,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val labelRes = when (metricType) {
        AthleteMetricType.FTP_WATTS -> R.string.zone_thresholds_field_ftp
        AthleteMetricType.CP_WATTS -> R.string.zone_thresholds_field_cp
        AthleteMetricType.W_PRIME_KJ -> R.string.zone_thresholds_field_w_prime
        AthleteMetricType.LT1_PWR_BIKE_WATTS -> R.string.zone_thresholds_field_lt1_pwr
        AthleteMetricType.HR_MAX_RUN_BPM,
        AthleteMetricType.HR_MAX_BIKE_BPM -> R.string.zone_thresholds_field_hr_max
        AthleteMetricType.HR_REST_BPM -> R.string.zone_thresholds_field_hr_rest
        AthleteMetricType.LTHR_RUN_BPM,
        AthleteMetricType.LTHR_BIKE_BPM -> R.string.zone_thresholds_field_lthr
        AthleteMetricType.LT1_HR_RUN_BPM,
        AthleteMetricType.LT1_HR_BIKE_BPM -> R.string.zone_thresholds_field_lt1_hr
        AthleteMetricType.WEIGHT_KG -> R.string.athlete_profile_metric_weight
    }
    val unitText = when (metricType) {
        AthleteMetricType.FTP_WATTS,
        AthleteMetricType.CP_WATTS,
        AthleteMetricType.LT1_PWR_BIKE_WATTS -> "W"
        AthleteMetricType.W_PRIME_KJ -> "kJ"
        else -> "bpm"
    }
    val keyboardType = if (metricType == AthleteMetricType.W_PRIME_KJ) {
        KeyboardType.Decimal
    } else {
        KeyboardType.Number
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = stringResource(labelRes),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        suffix = { Text(unitText) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.testTag("threshold_input_${metricType.name}")
    )
}

/**
 * Embedded 2-Point Critical Power & W' Estimator modal bottom sheet triggered from
 * `Zones -> Power (Bike)` when `enableCriticalPowerModel` is enabled (`REQ-SET-136.1`,
 * `docs/design_guidelines.md` §5.3 / §5.8 / Rule 73 full-width stacked actions).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpWPrimeEstimatorDialog(
    onDismiss: () -> Unit,
    onApply: (cpWatts: Int, wPrimeKj: Double) -> Unit
) {
    var shortPowerInput by remember { mutableStateOf("400") }
    var shortSecInput by remember { mutableStateOf("180") }
    var longPowerInput by remember { mutableStateOf("310") }
    var longSecInput by remember { mutableStateOf("720") }

    val estimate = remember(shortPowerInput, shortSecInput, longPowerInput, longSecInput) {
        val p1 = shortPowerInput.trim().replace(',', '.').toDoubleOrNull()
        val t1 = shortSecInput.trim().toIntOrNull()
        val p2 = longPowerInput.trim().replace(',', '.').toDoubleOrNull()
        val t2 = longSecInput.trim().toIntOrNull()
        if (p1 != null && t1 != null && p2 != null && t2 != null) {
            PowerPhysiologyCalculator.estimateCpAndWPrime(p1, t1, p2, t2)
        } else {
            null
        }
    }

    AppModalBottomSheet(
        title = stringResource(R.string.athlete_profile_cp_estimator_title),
        icon = Icons.Default.Tune,
        onDismissRequest = onDismiss,
        showCloseButton = true,
        modifier = Modifier.testTag("cp_w_prime_estimator_sheet")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = shortPowerInput,
                    onValueChange = { shortPowerInput = it },
                    label = {
                        Text(
                            text = stringResource(R.string.athlete_profile_cp_estimator_short_power),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = shortSecInput,
                    onValueChange = { shortSecInput = it },
                    label = {
                        Text(
                            text = stringResource(R.string.athlete_profile_cp_estimator_short_sec),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = longPowerInput,
                    onValueChange = { longPowerInput = it },
                    label = {
                        Text(
                            text = stringResource(R.string.athlete_profile_cp_estimator_long_power),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = longSecInput,
                    onValueChange = { longSecInput = it },
                    label = {
                        Text(
                            text = stringResource(R.string.athlete_profile_cp_estimator_long_sec),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Rule 73: Full-width stacked primary CTA + secondary Cancel button (no side-by-side overflow in German)
            Button(
                enabled = estimate != null,
                onClick = {
                    estimate?.let { onApply(it.cpWattsRounded, it.wPrimeKj) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cp_estimator_apply_button")
            ) {
                Text(
                    text = estimate?.let {
                        stringResource(
                            R.string.athlete_profile_cp_estimator_apply,
                            it.cpWattsRounded,
                            it.wPrimeKj
                        )
                    } ?: stringResource(R.string.athlete_weight_save),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.zone_calc_cancel_button),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "PhysiologicalThresholds - Power All Models Light")
@Composable
private fun PhysiologicalThresholdsPowerLightPreview() {
    ATrainingTrackerTheme(darkTheme = false) {
        PhysiologicalThresholdsCard(
            zoneType = ZoneType.PWR_BIKE,
            tuningConfig = TuningConfig(
                enableCogganPowerMetrics = true,
                enableCriticalPowerModel = true,
                enableAerobicEfficiencyModel = true
            ),
            values = ZoneThresholdsValues(ftp = 280, cp = 272, wPrimeKj = 19.5f, lt1PwrBike = 215),
            onSaveThreshold = { _, _ -> },
            onSaveCpAndWPrime = { _, _ -> },
            onOpenRecalculateDialog = {}
        )
    }
}

@Preview(showBackground = true, name = "PhysiologicalThresholds - HR Run Dark AMOLED")
@Composable
private fun PhysiologicalThresholdsHrDarkPreview() {
    ATrainingTrackerTheme(darkTheme = true, amoled = true) {
        PhysiologicalThresholdsCard(
            zoneType = ZoneType.HR_RUN,
            tuningConfig = TuningConfig(
                enablePmcModel = true,
                enableAerobicEfficiencyModel = true
            ),
            values = ZoneThresholdsValues(hrMax = 192, hrRest = 50, lthr = 172, lt1Hr = 148),
            onSaveThreshold = { _, _ -> },
            onSaveCpAndWPrime = { _, _ -> },
            onOpenRecalculateDialog = {}
        )
    }
}
