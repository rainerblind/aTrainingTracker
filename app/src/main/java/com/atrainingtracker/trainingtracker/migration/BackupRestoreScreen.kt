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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterSelectionDialog
import com.atrainingtracker.trainingtracker.ui.components.MetricItem
import com.atrainingtracker.trainingtracker.ui.map.createSensorMarker
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.*

data class MappingData(val uri: Uri, val analysis: ImportEngine.AnalysisResult)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: BackupRestoreViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val lastBackupInfo by viewModel.lastBackupInfo.collectAsState()
    
    val scrollState = rememberScrollState()
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showDropboxRestoreConfirm by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    var showMappingDialog by remember { mutableStateOf<MappingData?>(null) }
    
    val isBusy = uiState is BackupRestoreViewModel.UiState.Loading || uiState is BackupRestoreViewModel.UiState.Progress

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

    val pickLegacyFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importLegacyFile(context, it, "tcx")
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
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.backup_restore),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(16.dp),
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
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.create_backup))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.uploadToDropbox(context) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.create_and_upload_to_dropbox))
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.full_restore_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { pickFullRestoreLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        enabled = !isBusy
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Local: " + stringResource(R.string.restore_backup))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showDropboxRestoreConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        enabled = !isBusy
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.incremental_import_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { pickImportLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.import_workouts))
                    }
                }
            }

            // --- Legacy Recovery Card ---
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.legacy_recovery_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.legacy_recovery_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.bulkRecoverLegacyData(context, "tcx") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy
                        ) {
                            Text(stringResource(R.string.scan_tcx))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { pickLegacyFileLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.import_single_file))
                    }
                }
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

    if (showDropboxRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showDropboxRestoreConfirm = false },
            title = { Text(stringResource(R.string.restore_warning_title)) },
            text = { Text(stringResource(R.string.restore_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDropboxRestoreConfirm = false
                        viewModel.restoreFromDropbox(context)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.restore_backup))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDropboxRestoreConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (uiState is BackupRestoreViewModel.UiState.ClusterNamingRequired) {
        val namingState = uiState as BackupRestoreViewModel.UiState.ClusterNamingRequired
        ClusterNamingDialog(
            state = namingState,
            onConfirm = { clusterId, name -> viewModel.provideClusterDecision(clusterId, name) },
            onDismiss = { viewModel.provideClusterDecision(null, null) }
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
fun ClusterNamingDialog(
    state: BackupRestoreViewModel.UiState.ClusterNamingRequired,
    onConfirm: (Long?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val localContext = LocalContext.current
    var name by remember { mutableStateOf("") }
    var selectedCluster by remember { mutableStateOf<WorkoutCluster?>(null) }
    var showSelectionDialog by remember { mutableStateOf(false) }

    val decodedPoints = remember(state.polyline) { PolyUtil.decode(state.polyline) }
    val bounds = remember(decodedPoints) {
        if (decodedPoints.isEmpty()) return@remember null
        val b = LatLngBounds.builder()
        decodedPoints.forEach { b.include(it) }
        b.build()
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(bounds?.center ?: LatLng(0.0, 0.0), 12f)
    }

    LaunchedEffect(bounds) {
        bounds?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(it, 50))
        }
    }

    if (showSelectionDialog) {
        val clusterEngine = remember { WorkoutClusterEngine.getInstance(localContext) }
        val candidatesWithScores = remember(state.existingClusters) {
            clusterEngine.scoreClusters(state.existingClusters, state.start, state.end, state.apex, state.distance)
        }

        WorkoutClusterSelectionDialog(
            title = "Select Existing Route",
            candidates = candidatesWithScores,
            onSelect = { 
                selectedCluster = it
                showSelectionDialog = false
            },
            onDismiss = { showSelectionDialog = false },
            sportNameResolver = { SportTypeDatabaseManager.getInstance(localContext).getUIName(it) },
            bSportTypeResolver = { SportTypeDatabaseManager.getInstance(localContext).getBSportType(it) }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Workout to Route") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Found a recurring route from ${state.date}.")

                // --- ATT-304: Show sport type & distance ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val distanceFormatter = remember { DistanceFormatter() }
                    MetricItem(
                        iconRes = R.drawable.ic_distance,
                        value = distanceFormatter.format_with_units(state.distance),
                        isPrimary = true
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            painter = painterResource(id = state.bSportType.iconResId),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified
                        )
                        val sportName = remember(state.bSportType) {
                            val sportId = SportTypeDatabaseManager.getSportTypeId(state.bSportType)
                            SportTypeDatabaseManager.getInstance(localContext).getUIName(sportId)
                        }
                        Text(
                            text = sportName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp))) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
                    ) {
                        Polyline(
                            points = decodedPoints,
                            color = MaterialTheme.colorScheme.primary,
                            width = 5f,
                            jointType = JointType.ROUND,
                            startCap = RoundCap(),
                            endCap = RoundCap()
                        )
                        Marker(
                            state = rememberMarkerState(position = state.start), 
                            title = "Start",
                            icon = remember { createSensorMarker(localContext, R.drawable.control_start, TTColor.StartPoint) }
                        )
                        Marker(
                            state = rememberMarkerState(position = state.end), 
                            title = "End",
                            icon = remember { createSensorMarker(localContext, R.drawable.control_stop, TTColor.EndPoint) }
                        )
                        Marker(
                            state = rememberMarkerState(position = state.apex), 
                            title = "Apex",
                            icon = remember { createSensorMarker(localContext, R.drawable.ic_distance, TTColor.ApexPoint) }
                        )
                    }
                }

                // --- ATT-305: Same dropdown (using button + dialog pattern) ---
                Text("Route Assignment:", style = MaterialTheme.typography.labelLarge)
                
                OutlinedTextField(
                    value = selectedCluster?.name ?: "Create New...",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().clickable { showSelectionDialog = true },
                    label = { Text("Selected Route") },
                    trailingIcon = {
                        IconButton(onClick = { showSelectionDialog = true }) {
                            Icon(painter = painterResource(id = R.drawable.my_locations), contentDescription = null)
                        }
                    }
                )

                if (selectedCluster == null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Or give it a new name:", style = MaterialTheme.typography.labelLarge)
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("New Route Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                if (selectedCluster != null) {
                    onConfirm(selectedCluster!!.id, null)
                } else {
                    onConfirm(null, name.takeIf { it.isNotBlank() })
                }
            }) {
                Text(stringResource(R.string.OK))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Leave Unclustered")
            }
        }
    )
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
