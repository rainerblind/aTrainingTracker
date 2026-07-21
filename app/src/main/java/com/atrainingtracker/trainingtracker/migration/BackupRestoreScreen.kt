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
import java.text.DateFormat
import java.util.Date
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
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
    val lastBackupInfo by viewModel.lastBackupInfo.collectAsState()
    
    val scrollState = rememberScrollState()
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
        // ATT-288: Scroll to top when a status card or progress appears
        if (state !is BackupRestoreViewModel.UiState.Idle) {
            scrollState.animateScrollTo(0)
        }
    }

    Scaffold(
        modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                windowInsets = WindowInsets(0.dp),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- State Overlays (ATT-288: Shown at the very top) ---
            when (val state = uiState) {
                is BackupRestoreViewModel.UiState.Loading -> {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = state.message ?: stringResource(R.string.please_wait),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                is BackupRestoreViewModel.UiState.Progress -> {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            LinearProgressIndicator(
                                progress = { state.current.toFloat() / state.total.toFloat() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.import_processing_format, state.name),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                is BackupRestoreViewModel.UiState.Success -> {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(state.message, style = MaterialTheme.typography.bodyMedium)
                            }
                            TextButton(onClick = { viewModel.clearState() }) {
                                Text(stringResource(R.string.OK))
                            }
                        }
                    }
                }
                is BackupRestoreViewModel.UiState.Error -> {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(state.message, style = MaterialTheme.typography.bodyMedium)
                            }
                            TextButton(onClick = { viewModel.clearState() }) {
                                Text(stringResource(R.string.OK))
                            }
                        }
                    }
                }
                else -> {}
            }

            Text(
                text = stringResource(R.string.backup_restore_summary),
                style = MaterialTheme.typography.bodyLarge
            )

            // --- Backup Card ---
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
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

            // --- Automated Backups Card ---
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.automated_backups),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.automated_backups))
                            Text(
                                text = stringResource(R.string.automated_backups_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = viewModel.automatedBackupsEnabled,
                            onCheckedChange = { viewModel.updateAutomatedBackupsEnabled(it) },
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    if (viewModel.automatedBackupsEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.backup_interval), style = MaterialTheme.typography.labelLarge)
                        
                        val intervals = listOf(1, 3, 7, 30)
                        val labels = listOf(
                            stringResource(R.string.backup_interval_daily),
                            stringResource(R.string.backup_interval_3days),
                            stringResource(R.string.backup_interval_weekly),
                            stringResource(R.string.backup_interval_monthly)
                        )

                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            val currentLabel = labels[intervals.indexOf(viewModel.backupIntervalDays).coerceAtLeast(0)]
                            TextField(
                                value = currentLabel,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                colors = ExposedDropdownMenuDefaults.textFieldColors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                intervals.forEachIndexed { index, days ->
                                    DropdownMenuItem(
                                        text = { Text(labels[index]) },
                                        onClick = {
                                            viewModel.updateBackupIntervalDays(days)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        lastBackupInfo?.let { info ->
                            Spacer(modifier = Modifier.height(12.dp))
                            val dateStr = DateFormat.getDateTimeInstance().format(Date(info.timestamp))
                            val isSuccess = info.status == "SUCCESS"
                            Text(
                                text = if (isSuccess) {
                                    stringResource(R.string.last_backup, dateStr)
                                } else {
                                    "Last backup failed: ${info.status}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // --- Restore Card ---
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
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
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
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
            
            // Extra padding at the bottom to avoid conflict with the system navigation bar
            Spacer(modifier = Modifier.height(32.dp))
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
                                    colors = ExposedDropdownMenuDefaults.textFieldColors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    )
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
                                    colors = ExposedDropdownMenuDefaults.textFieldColors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    )
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
