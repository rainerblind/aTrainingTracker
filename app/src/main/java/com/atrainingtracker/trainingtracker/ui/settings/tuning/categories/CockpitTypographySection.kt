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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontFamily
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontWeight
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitTypography

/**
 * Cockpit typography tuning category composable (REQ-UI-262).
 * Encapsulates font family dropdown (constrained to 360dp max height) and font weight picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CockpitTypographySection(
    cockpitFontFamily: CockpitFontFamily,
    onFontFamilyChange: (CockpitFontFamily) -> Unit,
    cockpitFontWeight: CockpitFontWeight,
    onFontWeightChange: (CockpitFontWeight) -> Unit
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
                verticalArrangement = Arrangement.spacedBy(6.dp)
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "148",
                            fontFamily = previewFamily,
                            fontWeight = previewWeight,
                            fontSize = 30.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "bpm",
                            fontFamily = previewFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "28.5",
                            fontFamily = previewFamily,
                            fontWeight = previewWeight,
                            fontSize = 30.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "km/h",
                            fontFamily = previewFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "1:24:35",
                            fontFamily = previewFamily,
                            fontWeight = previewWeight,
                            fontSize = 30.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "TIME",
                            fontFamily = previewFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
