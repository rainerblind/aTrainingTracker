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

package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.theme.safePainterResource
import com.atrainingtracker.trainingtracker.ui.aftermath.PolarPendingRetrySyncSection
import com.atrainingtracker.trainingtracker.ui.aftermath.RpeFeelEditorControls
import com.atrainingtracker.trainingtracker.ui.clusters.EditWorkoutClusterDialog
import com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterSelectionDialog
import com.atrainingtracker.trainingtracker.ui.components.DropdownSelector
import com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions
import com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheet

object EditWorkoutTestTags {
    const val POLAR_RETRY_SYNC_BANNER = "edit_workout_polar_retry_sync_banner"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkoutScreen(
    viewModel: EditWorkoutViewModel,
    onBack: () -> Unit
) {
    EditWorkoutDialog(
        viewModel = viewModel,
        onDismiss = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkoutDialog(
    viewModel: EditWorkoutViewModel,
    onDismiss: () -> Unit
) {
    // Observe LiveData from ViewModel
    val workoutData by viewModel.workoutData.collectAsState()
    val cardPrefs by viewModel.cardPreferences.collectAsState()
    val sportTypes by viewModel.sportTypeNames.observeAsState(emptyList())
    val equipmentNames by viewModel.equipmentNames.observeAsState(emptyList())

    val localContext = androidx.compose.ui.platform.LocalContext.current
    val isHealthConnectAvailable = remember {
        com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectManager.isAvailable(localContext)
    }
    val syncToHealthConnect = workoutData?.syncToHealthConnect ?: TrainingApplication.syncToHealthConnect()

    AppModalBottomSheet(
        title = stringResource(R.string.edit_workout),
        icon = Icons.Default.Edit,
        onDismissRequest = onDismiss,
        actions = {
            AppDialogActions.SaveCancel(
                onSave = {
                    viewModel.saveChanges()
                    onDismiss()
                },
                onCancel = onDismiss
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Diagnostic Warning Card (REQ-TRK-053.4)
            val recordingErrors = workoutData?.recordingErrors
            if (!recordingErrors.isNullOrBlank()) {
                var showEditDiagnosticSheet by remember { mutableStateOf(false) }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEditDiagnosticSheet = true }
                        .testTag(com.atrainingtracker.trainingtracker.ui.dialogs.WorkoutDiagnosticTestTags.CARD)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.workout_diagnostic_banner_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.workout_diagnostic_banner_action),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (showEditDiagnosticSheet) {
                    com.atrainingtracker.trainingtracker.ui.dialogs.WorkoutDiagnosticBottomSheet(
                        recordingErrorsJson = recordingErrors,
                        workoutName = workoutData?.workoutName ?: "",
                        sportName = workoutData?.sportName ?: "",
                        onClearWarning = {
                            viewModel.clearRecordingErrors()
                            showEditDiagnosticSheet = false
                        },
                        onDismiss = { showEditDiagnosticSheet = false }
                    )
                }
            }

            // 0b. Polar H10 / Polar Verity Sense Pending Retry Sync Banner (REQ-SET-127, ATT-9174)
            val currentWorkout = workoutData
            if (currentWorkout != null && currentWorkout.polarSyncPending) {
                PolarPendingRetrySyncSection(
                    workoutId = currentWorkout.id,
                    polarExerciseId = currentWorkout.polarExerciseId,
                    polarDeviceId = currentWorkout.polarDeviceId,
                    onDismiss = { viewModel.dismissPolarSyncPending() },
                    outerPadding = PaddingValues(0.dp),
                    modifier = Modifier.testTag(EditWorkoutTestTags.POLAR_RETRY_SYNC_BANNER)
                )
            }

            // 1. Workout Name (Mandatory Core Anchor)
            OutlinedTextField(
                value = workoutData?.workoutName ?: "",
                onValueChange = { viewModel.updateWorkoutName(it) },
                label = { Text(stringResource(R.string.hint_workout_name)) },
                modifier = Modifier.fillMaxWidth()
            )

            // 2. Route / Cluster Assignment (ATT-318)
            val suggestions by viewModel.clusterSuggestions.collectAsState()
            val currentClusterId = workoutData?.clusterId ?: -1L
            val currentClusterName = workoutData?.clusterName
            var showClusterDialog by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = currentClusterName ?: stringResource(R.string.unclustered),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.cluster_naming__selected_route_label)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_favorite_route),
                            contentDescription = null,
                            tint = if (currentClusterId > 0) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = if (currentClusterId > 0) {
                        {
                            IconButton(onClick = { viewModel.unassignCluster() }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.cluster_naming__leave_unclustered),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                // Clickable overlay over text area to open cluster dialog on tap
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(end = if (currentClusterId > 0) 48.dp else 0.dp)
                        .clickable { showClusterDialog = true }
                )
            }

            if (showClusterDialog) {
                EditWorkoutClusterDialog(
                    currentClusterId = currentClusterId,
                    candidates = suggestions,
                    initialWorkoutName = workoutData?.workoutName ?: "",
                    onSelectCluster = { cluster ->
                        viewModel.applyClusterIdentity(cluster)
                        showClusterDialog = false
                    },
                    onUnassignCluster = {
                        viewModel.unassignCluster()
                        showClusterDialog = false
                    },
                    onCreateNewCluster = { newName, hasCounter ->
                        viewModel.createNewCluster(newName, hasCounter)
                        showClusterDialog = false
                    },
                    onDismiss = { showClusterDialog = false },
                    sportNameResolver = { viewModel.getSportName(it) },
                    bSportTypeResolver = { viewModel.getBSportType(it) }
                )
            }

            // 3. Spinners (Sport & Equipment) (Mandatory Core Anchors)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownSelector(
                    label = stringResource(R.string.Sport),
                    options = sportTypes,
                    selectedOption = viewModel.suggestedSportTypeName,
                    onOptionSelected = { viewModel.updateSportName(it) },
                    modifier = Modifier.weight(1f),
                    stayOpenOn = setOf(viewModel.allSportTypes)
                )
                DropdownSelector(
                    label = stringResource(R.string.Equipment),
                    options = equipmentNames,
                    selectedOption = viewModel.suggestedEquipmentName ?: viewModel.noEquipment,
                    onOptionSelected = { viewModel.updateEquipmentName(it) },
                    modifier = Modifier.weight(1f),
                    stayOpenOn = setOf(viewModel.allShoes, viewModel.allBikes)
                )
            }

            // 4. Two-Tier Workout Classification Tags: Routine Context (Commute/Indoor) vs Special Distinction (Race/Epic) (REQ-REV-018.1, ATT-9022)
            WorkoutClassificationSection(
                isCommute = workoutData?.commute ?: false,
                isTrainer = workoutData?.trainer ?: false,
                isRace = workoutData?.race ?: false,
                isEpic = workoutData?.epic ?: false,
                onCommuteChange = { viewModel.updateIsCommute(it) },
                onTrainerChange = { viewModel.updateIsTrainer(it) },
                onRaceChange = { viewModel.updateIsRace(it) },
                onEpicChange = { viewModel.updateIsEpic(it) }
            )

            // 5. Grouped Cloud Sync Card (Strava and/or Health Connect, ATT-7517)
            val showStrava = TrainingApplication.uploadToCommunity(FileFormat.STRAVA)
            val showHealthConnect = isHealthConnectAvailable
            if (showStrava || showHealthConnect) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column {
                        // 5a. Strava Sync Row
                        if (showStrava) {
                            val uploadStatus = workoutData?.uploadToStrava ?: -1
                            val stravaMappingAvailable = workoutData?.stravaSportName != null

                            val isChecked = if (stravaMappingAvailable) {
                                when (uploadStatus) {
                                    1 -> true
                                    0 -> false
                                    else -> true
                                }
                            } else false

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = isChecked,
                                        enabled = stravaMappingAvailable,
                                        role = Role.Checkbox,
                                        onValueChange = { viewModel.updateUploadToStrava(it) }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = safePainterResource(id = R.drawable.logo_square_strava),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .alpha(if (stravaMappingAvailable) 1f else 0.38f),
                                    tint = Color.Unspecified
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.stravaUpload),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (stravaMappingAvailable) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null,
                                    enabled = stravaMappingAvailable
                                )
                            }
                        }

                        if (showStrava && showHealthConnect) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }

                        // 5b. Health Connect Sync Row
                        if (showHealthConnect) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = syncToHealthConnect,
                                        role = Role.Checkbox,
                                        onValueChange = { viewModel.updateSyncToHealthConnect(it) }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = safePainterResource(id = R.drawable.ic_health_connect, fallback = Icons.Default.Favorite),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.Unspecified
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.health_connect_sync_manual),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = syncToHealthConnect,
                                    onCheckedChange = null
                                )
                            }
                        }
                    }
                }
            }

            // 5c. RPE and Feel (REQ-WKO-036)
            val showRpeAndFeel = cardPrefs.showRpeAndFeel || (workoutData?.rpe != null) || (workoutData?.feel != null)
            if (showRpeAndFeel) {
                RpeFeelEditorControls(
                    rpe = workoutData?.rpe,
                    feel = workoutData?.feel,
                    onRpeChange = { viewModel.updateRpe(it) },
                    onFeelChange = { viewModel.updateFeel(it) }
                )
            }

            // 6. Description
            OutlinedTextField(
                value = workoutData?.description ?: "",
                onValueChange = { newValue -> viewModel.updateDescription(newDescription = newValue) },
                label = { Text(stringResource(R.string.hint_workout_description)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            // 7. Goal
            OutlinedTextField(
                value = workoutData?.goal ?: "",
                onValueChange = { newValue -> viewModel.updateGoal(newGoal = newValue) },
                label = { Text(stringResource(R.string.hint_workout_goal)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 8. Method
            OutlinedTextField(
                value = workoutData?.method ?: "",
                onValueChange = { newValue -> viewModel.updateMethod(newMethod = newValue) },
                label = { Text(stringResource(R.string.hint_workout_method)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 9. Crop workout action
            var showCropScreen by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showCropScreen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.crop_workout_title))
            }

            if (showCropScreen) {
                workoutData?.id?.let { wId ->
                    com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropWorkoutScreen(
                        workoutId = wId,
                        onDismiss = {
                            showCropScreen = false
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Deterministic semantics test tags for the two-tier workout classification section in
 * [EditWorkoutScreen] (`REQ-REV-018.1`, `ATT-9022`).
 */
internal object EditWorkoutClassificationTestTags {
    const val ROUTINE_GROUP = "edit_workout_classification_routine_group"
    const val SPECIAL_GROUP = "edit_workout_classification_special_group"
    const val COMMUTE_TILE = "edit_workout_tag_commute"
    const val INDOOR_TILE = "edit_workout_tag_indoor"
    const val RACE_TILE = "edit_workout_tag_race"
    const val EPIC_TILE = "edit_workout_tag_epic"
}

/**
 * Two-tier workout classification selector separating everyday routine conditions (`Commute`,
 * `Indoor` — calm low-emphasis styling, mutually exclusive via ViewModel) from memorable special
 * distinctions (`Race`, `Epic` — elevated standout accent styling, independently toggleable
 * including together) (`REQ-REV-018.1`, `ATT-9022`).
 */
@Composable
internal fun WorkoutClassificationSection(
    isCommute: Boolean,
    isTrainer: Boolean,
    isRace: Boolean,
    isEpic: Boolean,
    onCommuteChange: (Boolean) -> Unit,
    onTrainerChange: (Boolean) -> Unit,
    onRaceChange: (Boolean) -> Unit,
    onEpicChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val epicColors = TTColor.epicBadgeColors()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tier 1: Routine Context (calm, low-emphasis styling)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EditWorkoutClassificationTestTags.ROUTINE_GROUP),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.workout_classification_routine_header),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Commute Tile (calm routine tone)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isCommute) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                        .border(
                            width = if (isCommute) 1.5.dp else 0.5.dp,
                            color = if (isCommute) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .toggleable(
                            value = isCommute,
                            role = Role.Checkbox,
                            onValueChange = onCommuteChange
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag(EditWorkoutClassificationTestTags.COMMUTE_TILE),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Commute,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isCommute) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.commute),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isCommute) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isCommute) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Indoor Tile (calm routine tone)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isTrainer) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                        .border(
                            width = if (isTrainer) 1.5.dp else 0.5.dp,
                            color = if (isTrainer) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .toggleable(
                            value = isTrainer,
                            role = Role.Checkbox,
                            onValueChange = onTrainerChange
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag(EditWorkoutClassificationTestTags.INDOOR_TILE),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isTrainer) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.indoor_short),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isTrainer) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isTrainer) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Tier 2: Special Distinction (elevated, standout styling; independently toggleable)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EditWorkoutClassificationTestTags.SPECIAL_GROUP),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.workout_classification_special_header),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Race Tile (EmojiEvents + Royal Blue tertiary accent matching WorkoutHeader)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isRace) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (isRace) 2.dp else 1.dp,
                        color = if (isRace) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                        }
                    ),
                    shadowElevation = if (isRace) 2.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 46.dp)
                        .toggleable(
                            value = isRace,
                            role = Role.Checkbox,
                            onValueChange = onRaceChange
                        )
                        .testTag(EditWorkoutClassificationTestTags.RACE_TILE)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isRace) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
                            }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.race),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isRace) FontWeight.Bold else FontWeight.Medium,
                            color = if (isRace) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Epic Tile (AutoAwesome + warm Gold/Amber TTColor accent matching WorkoutHeader)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isEpic) {
                        epicColors.container
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (isEpic) 2.dp else 1.dp,
                        color = if (isEpic) {
                            epicColors.accent
                        } else {
                            epicColors.accent.copy(alpha = 0.4f)
                        }
                    ),
                    shadowElevation = if (isEpic) 2.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 46.dp)
                        .toggleable(
                            value = isEpic,
                            role = Role.Checkbox,
                            onValueChange = onEpicChange
                        )
                        .testTag(EditWorkoutClassificationTestTags.EPIC_TILE)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isEpic) {
                                epicColors.accent
                            } else {
                                epicColors.accent.copy(alpha = 0.8f)
                            }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.epic),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isEpic) FontWeight.Bold else FontWeight.Medium,
                            color = if (isEpic) {
                                epicColors.onContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Classification Two-Tier Light", showBackground = true)
@Preview(name = "Classification Two-Tier Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WorkoutClassificationSectionPreview() {
    ATrainingTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            WorkoutClassificationSection(
                isCommute = true,
                isTrainer = false,
                isRace = true,
                isEpic = true,
                onCommuteChange = {},
                onTrainerChange = {},
                onRaceChange = {},
                onEpicChange = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "Classification Unselected Light", showBackground = true)
@Preview(name = "Classification Unselected Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WorkoutClassificationSectionUnselectedPreview() {
    ATrainingTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            WorkoutClassificationSection(
                isCommute = false,
                isTrainer = false,
                isRace = false,
                isEpic = false,
                onCommuteChange = {},
                onTrainerChange = {},
                onRaceChange = {},
                onEpicChange = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
