package com.atrainingtracker.banalservice.ui.sporttype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isEmpty
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R

import com.atrainingtracker.trainingtracker.MyHelper

@Composable
fun EditSportTypeDialog(
    item: SportTypeItem,
    onDismiss: () -> Unit,
    onConfirm: (SportTypeItem) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var minSpeed by remember { mutableStateOf(MyHelper.mps2userUnit(item.minSpeed).toString()) }
    var maxSpeed by remember { mutableStateOf(MyHelper.mps2userUnit(item.maxSpeed).toString()) }
    var stravaName by remember { mutableStateOf(item.stravaName) }
    var tcxName by remember { mutableStateOf(item.tcxName) }
    var gcName by remember { mutableStateOf(item.gcName) }

    val speedUnit = stringResource(MyHelper.getSpeedUnitNameId())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (item.id == -1L) stringResource(R.string.text_new) else stringResource(R.string.edit_sport_type))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Speeds Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minSpeed,
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) minSpeed = it },
                        label = { Text("${stringResource(R.string.min_avg_speed)} ($speedUnit)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = maxSpeed,
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) maxSpeed = it },
                        label = { Text("${stringResource(R.string.max_avg_speed)} ($speedUnit)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(stringResource(R.string.prefs_Export), style = MaterialTheme.typography.labelLarge)

                // Strava Mapping
                OutlinedTextField(
                    value = stravaName,
                    onValueChange = { stravaName = it },
                    label = { Text("Strava Sport Name") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(painterResource(R.drawable.logo_square_strava), null,
                        Modifier.size(18.dp), tint = Color.Unspecified) }
                )

                // TCX Mapping
                OutlinedTextField(
                    value = tcxName,
                    onValueChange = { tcxName = it },
                    label = { Text("TCX Sport Name") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Save, null) }
                )

                // GoldenCheetah Mapping
                OutlinedTextField(
                    value = gcName,
                    onValueChange = { gcName = it },
                    label = { Text("GoldenCheetah Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalMin = MyHelper.UserUnit2mps(minSpeed.toDoubleOrNull() ?: 0.0)
                    val finalMax = MyHelper.UserUnit2mps(maxSpeed.toDoubleOrNull() ?: 0.0)
                    onConfirm(item.copy(
                        name = name,
                        minSpeed = finalMin,
                        maxSpeed = finalMax,
                        stravaName = stravaName,
                        tcxName = tcxName,
                        gcName = gcName
                    ))
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.Cancel))
            }
        }
    )
}