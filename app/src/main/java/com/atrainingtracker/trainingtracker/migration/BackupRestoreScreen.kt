/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager

data class MappingData(val uri: Uri, val analysis: ImportEngine.AnalysisResult)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: BackupRestoreViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    var showMappingDialog by remember { mutableStateOf<MappingData?>(null) }

    val pickFullRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            restoreUri = it
            showRestoreConfirm = true
        }
    }

    val pickImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.analyzeImport(context, it)
        }
    }

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is BackupRestoreViewModel.UiState.MappingRequired) {
            showMappingDialog = MappingData(state.uri, state.analysis)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.backup_restore_summary),
                style = MaterialTheme.typography.bodyLarge
            )

            // --- Backup Card ---
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.create_backup),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val chooserTitle = stringResource(R.string.create_backup)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.createBackup(context) { uri ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, chooserTitle))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.create_backup))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.uploadToDropbox(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Backup to Dropbox")
                    }
                }
            }

            // --- Restore Card ---
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.restore_backup),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { pickFullRestoreLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Local: " + stringResource(R.string.restore_backup))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.restoreFromDropbox(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dropbox: " + stringResource(R.string.restore_backup))
                    }
                }
            }

            // --- Import Card ---
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.import_workouts),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { pickImportLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.import_workouts))
                    }
                }
            }
            
            // --- State Overlays ---
            when (val state = uiState) {
                is BackupRestoreViewModel.UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is BackupRestoreViewModel.UiState.Progress -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = { state.current.toFloat() / state.total.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.import_processing_format, state.name))
                    }
                }
                is BackupRestoreViewModel.UiState.Success -> {
                    Text(state.message, color = MaterialTheme.colorScheme.primary)
                    Button(onClick = { viewModel.clearState() }) { Text(stringResource(R.string.OK)) }
                }
                is BackupRestoreViewModel.UiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.clearState() }) { Text(stringResource(R.string.OK)) }
                }
                else -> {}
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.restore_warning_title)) },
            text = { Text(stringResource(R.string.restore_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirm = false
                        restoreUri?.let { viewModel.performFullRestore(context, it) }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.restore_backup))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    showMappingDialog?.let { data ->
        ImportMappingDialog(
            analysis = data.analysis,
            onConfirm = { sportMapping, equipMapping ->
                viewModel.performIncrementalImport(context, data.uri, sportMapping, equipMapping)
                showMappingDialog = null
            },
            onDismiss = {
                showMappingDialog = null
                viewModel.clearState()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportMappingDialog(
    analysis: ImportEngine.AnalysisResult,
    onConfirm: (Map<Long, Long>, Map<Long, Long>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val localSportTypes = remember { SportTypeDatabaseManager.getInstance(context).getSportTypes(null) }
    val localEquipment = remember { com.atrainingtracker.trainingtracker.database.EquipmentDbHelper(context).equipmentItems }

    val sportMapping = remember {
        mutableStateMapOf<Long, Long>().apply {
            analysis.sportTypes.forEach { source ->
                val exactMatch = localSportTypes.find { it.name.equals(source.name, ignoreCase = true) }
                this[source.id] = exactMatch?.id ?: SportTypeDatabaseManager.getSportTypeId(source.bSportType)
            }
        }
    }

    val equipMapping = remember {
        mutableStateMapOf<Long, Long>().apply {
            analysis.equipment.forEach { source ->
                val exactMatch = localEquipment.find { it.name.equals(source.name, ignoreCase = true) }
                this[source.id] = exactMatch?.id ?: -1L
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Map Data for Import") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (analysis.sportTypes.isNotEmpty()) {
                    Text("Sport Types", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    analysis.sportTypes.forEach { source ->
                        Column {
                            Text("${source.name} (${source.bSportType})", style = MaterialTheme.typography.labelMedium)
                            var expanded by remember { mutableStateOf(false) }
                            val currentTarget = localSportTypes.find { it.id == sportMapping[source.id] }
                            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                                TextField(
                                    value = currentTarget?.name ?: "Unknown",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    colors = ExposedDropdownMenuDefaults.textFieldColors()
                                )
                                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    localSportTypes.forEach { local ->
                                        DropdownMenuItem(text = { Text(local.name) }, onClick = {
                                            sportMapping[source.id] = local.id
                                            expanded = false
                                        })
                                    }
                                }
                            }
                        }
                    }
                }

                if (analysis.equipment.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Equipment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    analysis.equipment.forEach { source ->
                        Column {
                            Text("${source.name} (${source.bSportType})", style = MaterialTheme.typography.labelMedium)
                            var expanded by remember { mutableStateOf(false) }
                            val currentTarget = localEquipment.find { it.id == equipMapping[source.id] }
                            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                                TextField(
                                    value = currentTarget?.name ?: "None",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    colors = ExposedDropdownMenuDefaults.textFieldColors()
                                )
                                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    DropdownMenuItem(text = { Text("None") }, onClick = {
                                        equipMapping[source.id] = -1L
                                        expanded = false
                                    })
                                    localEquipment.forEach { local ->
                                        DropdownMenuItem(text = { Text(local.name) }, onClick = {
                                            equipMapping[source.id] = local.id
                                            expanded = false
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(sportMapping.toMap(), equipMapping.toMap()) }) {
                Text(stringResource(R.string.OK))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
