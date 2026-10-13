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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldStyle
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldVariant
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontFamily
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontWeight
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitTypography

/**
 * Cockpit typography & tile design tuning category composable (REQ-UI-262, REQ-UI-258, REQ-UI-276, ATT-2456).
 * Encapsulates font family dropdown, font weight picker, cockpit tile visual variant selector,
 * and granular corner radius, border thickness, and border contrast sliders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CockpitTypographySection(
    cockpitFontFamily: CockpitFontFamily,
    onFontFamilyChange: (CockpitFontFamily) -> Unit,
    cockpitFontWeight: CockpitFontWeight,
    onFontWeightChange: (CockpitFontWeight) -> Unit,
    sensorFieldVariant: SensorFieldVariant = SensorFieldVariant.CLASSIC_SEAMLESS,
    onSensorFieldVariantChange: (SensorFieldVariant) -> Unit = {},
    sensorFieldCornerRadius: Float = 0.0f,
    onCornerRadiusChange: (Float) -> Unit = {},
    sensorFieldBorderThickness: Float = 1.0f,
    onBorderThicknessChange: (Float) -> Unit = {},
    sensorFieldBorderContrast: Float = 0.0f,
    onBorderContrastChange: (Float) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Font Family Selector
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.tuning_cockpit_font_family_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = stringResource(cockpitFontFamily.getDisplayNameRes()),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = CockpitTypography.resolveFontFamily(cockpitFontFamily),
                        fontWeight = cockpitFontWeight.asFontWeight()
                    )
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    CockpitFontFamily.values().forEach { family ->
                        val itemFontFamily = remember(family) {
                            CockpitTypography.resolveFontFamily(family)
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(family.getDisplayNameRes()),
                                    fontFamily = itemFontFamily,
                                    fontWeight = cockpitFontWeight.asFontWeight()
                                )
                            },
                            onClick = {
                                onFontFamilyChange(family)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // Boldness (Font Weight) Selector
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.tuning_cockpit_font_weight_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CockpitFontWeight.values().forEach { weight ->
                    FilterChip(
                        selected = cockpitFontWeight == weight,
                        onClick = { onFontWeightChange(weight) },
                        label = {
                            Text(
                                text = stringResource(weight.getDisplayNameRes()),
                                fontWeight = weight.asFontWeight(),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Cockpit Tile Design & Grid Style Selector (REQ-UI-258)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.tuning_sensor_field_variant_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            var variantExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = variantExpanded,
                onExpandedChange = { variantExpanded = !variantExpanded }
            ) {
                OutlinedTextField(
                    value = stringResource(sensorFieldVariant.titleResId),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = variantExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = variantExpanded,
                    onDismissRequest = { variantExpanded = false },
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    SensorFieldVariant.values().forEach { variant ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(variant.titleResId),
                                    fontWeight = if (sensorFieldVariant == variant) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSensorFieldVariantChange(variant)
                                val baseline = SensorFieldStyle.forVariant(variant)
                                onCornerRadiusChange(baseline.cornerRadius.value)
                                onBorderThicknessChange(baseline.borderThickness.value)
                                onBorderContrastChange(baseline.borderContrast)
                                variantExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Granular Tile Geometry & Border Sliders (ATT-2456 / REQ-UI-276)
        TuningSliderItem(
            title = stringResource(R.string.tuning_sensor_field_corner_radius_title),
            valueText = String.format(Locale.US, "%.0f dp", sensorFieldCornerRadius),
            helperText = stringResource(R.string.tuning_sensor_field_corner_radius_desc),
            defaultText = stringResource(R.string.tuning_sensor_field_corner_radius_default),
            value = sensorFieldCornerRadius,
            onValueChange = onCornerRadiusChange,
            valueRange = 0.0f..20.0f,
            steps = 19
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_sensor_field_border_thickness_title),
            valueText = String.format(Locale.US, "%.1f dp", sensorFieldBorderThickness),
            helperText = stringResource(R.string.tuning_sensor_field_border_thickness_desc),
            defaultText = stringResource(R.string.tuning_sensor_field_border_thickness_default),
            value = sensorFieldBorderThickness,
            onValueChange = onBorderThicknessChange,
            valueRange = 0.0f..4.0f,
            steps = 7
        )

        TuningSliderItem(
            title = stringResource(R.string.tuning_sensor_field_border_contrast_title),
            valueText = String.format(Locale.US, "%.0f%%", sensorFieldBorderContrast * 100f),
            helperText = stringResource(R.string.tuning_sensor_field_border_contrast_desc),
            defaultText = stringResource(R.string.tuning_sensor_field_border_contrast_default),
            value = sensorFieldBorderContrast,
            onValueChange = onBorderContrastChange,
            valueRange = 0.0f..1.0f,
            steps = 9
        )

        // Live Preview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.tuning_cockpit_preview_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val previewFamily = remember(cockpitFontFamily) {
                    CockpitTypography.resolveFontFamily(cockpitFontFamily)
                }
                val previewWeight = cockpitFontWeight.asFontWeight()
                val isDarkTheme = isSystemInDarkTheme()
                val baseStyle = remember(sensorFieldVariant) {
                    SensorFieldStyle.forVariant(sensorFieldVariant)
                }
                val tileShape = remember(sensorFieldCornerRadius) {
                    SensorFieldStyle.resolveShape(sensorFieldCornerRadius.dp)
                }
                val tileBorder = remember(sensorFieldBorderThickness, sensorFieldBorderContrast, isDarkTheme) {
                    SensorFieldStyle.resolveBorder(sensorFieldBorderThickness.dp, sensorFieldBorderContrast, isDarkTheme)
                }
                val tileElevation = if (baseStyle.defaultElevation > 0.dp) {
                    CardDefaults.cardElevation(defaultElevation = baseStyle.defaultElevation)
                } else {
                    CardDefaults.cardElevation(defaultElevation = 0.dp)
                }
                val effectiveSpacing = remember(sensorFieldVariant, sensorFieldCornerRadius, sensorFieldBorderThickness) {
                    SensorFieldStyle.resolveGridSpacing(
                        variant = sensorFieldVariant,
                        cornerRadius = sensorFieldCornerRadius.dp,
                        borderThickness = sensorFieldBorderThickness.dp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (effectiveSpacing > 0.dp) {
                        Arrangement.spacedBy(effectiveSpacing, Alignment.CenterHorizontally)
                    } else {
                        Arrangement.Center
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PreviewCockpitTile(
                        modifier = Modifier.weight(1f),
                        shape = tileShape,
                        border = tileBorder,
                        borderThickness = sensorFieldBorderThickness.dp,
                        elevation = tileElevation,
                        value = "148",
                        unit = "bpm",
                        previewFamily = previewFamily,
                        previewWeight = previewWeight
                    )
                    PreviewCockpitTile(
                        modifier = Modifier.weight(1f),
                        shape = tileShape,
                        border = tileBorder,
                        borderThickness = sensorFieldBorderThickness.dp,
                        elevation = tileElevation,
                        value = "28.5",
                        unit = "km/h",
                        previewFamily = previewFamily,
                        previewWeight = previewWeight
                    )
                    PreviewCockpitTile(
                        modifier = Modifier.weight(1f),
                        shape = tileShape,
                        border = tileBorder,
                        borderThickness = sensorFieldBorderThickness.dp,
                        elevation = tileElevation,
                        value = "1:24:35",
                        unit = "TIME",
                        previewFamily = previewFamily,
                        previewWeight = previewWeight
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCockpitTile(
    modifier: Modifier = Modifier,
    shape: Shape,
    border: BorderStroke?,
    borderThickness: androidx.compose.ui.unit.Dp = 1.dp,
    elevation: CardElevation,
    value: String,
    unit: String,
    previewFamily: FontFamily,
    previewWeight: FontWeight
) {
    Card(
        modifier = modifier,
        shape = shape,
        border = border,
        elevation = elevation,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        val hPadding = (4.dp + borderThickness / 2f).coerceAtLeast(4.dp)
        val vPadding = (8.dp + borderThickness / 2f).coerceAtLeast(8.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = vPadding, horizontal = hPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                fontFamily = previewFamily,
                fontWeight = previewWeight,
                fontSize = if (value.length > 5) 18.sp else 22.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = unit,
                fontFamily = previewFamily,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
