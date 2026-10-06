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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.app.Application
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.CardElevation
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.database.RouteWithPath
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.LiveSegment
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap
import com.atrainingtracker.trainingtracker.ui.map.ElevationProfile
import com.atrainingtracker.trainingtracker.climbs.LiveClimbsRepository
import com.atrainingtracker.trainingtracker.routes.ForkNavigationRepository
import com.atrainingtracker.trainingtracker.routes.ReturnNavigationRepository
import com.atrainingtracker.trainingtracker.routes.ReturnNavigationState
import com.atrainingtracker.trainingtracker.routes.TurnByTurnNavigationRepository
import com.atrainingtracker.trainingtracker.ui.climbs.LiveClimbSheet
import com.atrainingtracker.trainingtracker.ui.routes.AutoDetectedRouteBanner
import com.atrainingtracker.trainingtracker.ui.routes.ForkDecisionCard
import com.atrainingtracker.trainingtracker.ui.routes.ReturnNavigationHud
import com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorViewModel
import com.atrainingtracker.trainingtracker.ui.routes.TurnPromptBanner
import com.atrainingtracker.trainingtracker.ui.segments.LiveSegmentSheet
import com.atrainingtracker.trainingtracker.ui.components.core.BottomSheetDesign
import com.atrainingtracker.trainingtracker.ui.components.core.sheetContour
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldState
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldStyle
import com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldView
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitTypography
import com.atrainingtracker.trainingtracker.ui.tracking.typography.LocalCockpitTypography
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.StateFlow

interface GridActions {
    fun onEditField(fieldState: SensorFieldState)
    fun onDeleteField(fieldState: SensorFieldState)
    fun onAddRow(beforeRow: Int)
    fun onAddCol(atRow: Int, beforeCol: Int)
    fun onSelectFieldForMove(fieldState: SensorFieldState) {}
    fun onCancelMove() {}
    fun onSwapFields(sourceFieldId: Long, targetFieldId: Long) {}
    fun onMoveField(sourceFieldId: Long, targetRow: Int, targetCol: Int) {}
}

interface TabToggleActions {
    fun onToggleMap(enabled: Boolean) {}
    fun onToggleElevationProfile(enabled: Boolean) {}
    fun onToggleLiveSegments(enabled: Boolean) {}
    fun onToggleLiveClimbs(enabled: Boolean) {}
    fun onToggleNavigationHints(enabled: Boolean) {}
    fun onToggleLapButton(enabled: Boolean) {}

    companion object {
        val Empty: TabToggleActions = object : TabToggleActions {}
    }
}

/**
 * A generic screen that displays a grid of sensor fields for either tracking or configuration.
 * It adapts its UI and behavior based on the provided [screenMode].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorGridScreen(
    state: TrackingScreenState,
    screenMode: ScreenMode,
    gridActions: GridActions,
    currentLocationFlow: StateFlow<LatLng?>,
    liveSegments: StateFlow<List<LiveSegment>>,
    selectedFieldForMove: SensorFieldState? = null,
    gridSpacing: Dp = 0.dp,
    fieldShape: Shape = RectangleShape,
    fieldElevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    routeSelectorViewModel: RouteSelectorViewModel? = null,
    tabToggleActions: TabToggleActions = TabToggleActions.Empty
) {
    val context = LocalContext.current
    val tuningDataStore = remember { TuningPreferencesDataStore(context) }
    val tuningConfig by tuningDataStore.tuningConfigFlow.collectAsState(
        initial = TuningConfig()
    )
    val activeVariantStyle = remember(tuningConfig.sensorFieldVariant) {
        SensorFieldStyle.forVariant(tuningConfig.sensorFieldVariant)
    }
    val isDefaultStyling = gridSpacing == 0.dp && fieldShape == RectangleShape
    val effectiveSpacing = if (isDefaultStyling) activeVariantStyle.gridSpacing else gridSpacing
    val effectiveShape = if (isDefaultStyling) {
        if (tuningConfig.sensorFieldCornerRadius > 0f) {
            SensorFieldStyle.resolveShape(tuningConfig.sensorFieldCornerRadius.dp)
        } else {
            activeVariantStyle.shape
        }
    } else fieldShape
    val effectiveElevation = if (isDefaultStyling) activeVariantStyle.elevation else fieldElevation
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val effectiveBorder = remember(
        tuningConfig.sensorFieldBorderThickness,
        tuningConfig.sensorFieldBorderContrast,
        isDarkTheme
    ) {
        SensorFieldStyle.resolveBorder(
            borderThickness = tuningConfig.sensorFieldBorderThickness.dp,
            borderContrast = tuningConfig.sensorFieldBorderContrast,
            isDarkTheme = isDarkTheme
        )
    }

    val cockpitTypography = remember(tuningConfig.cockpitFontFamily, tuningConfig.cockpitFontWeight) {
        CockpitTypography.resolveConfig(
            family = tuningConfig.cockpitFontFamily,
            weight = tuningConfig.cockpitFontWeight
        )
    }

    val activeSegments by liveSegments.collectAsState()
    val activeSegment = activeSegments.firstOrNull()

    val showLiveSegments = state.showLiveSegments && activeSegment != null

    val liveClimbsRepo = remember { LiveClimbsRepository.getInstance(context) }
    val activeLiveClimb by liveClimbsRepo.activeLiveClimb.collectAsState()
    val showLiveClimbs = !showLiveSegments && state.showLiveClimbs && tuningConfig.showLiveClimbs && activeLiveClimb != null

    val navRepo = remember { TurnByTurnNavigationRepository.getInstance(context) }
    val navState by navRepo.navigationState.collectAsState()

    val returnNavRepo = remember { ReturnNavigationRepository.getInstance(context) }
    val returnNavState by returnNavRepo.navigationState.collectAsState()

    val forkNavRepo = remember { ForkNavigationRepository.getInstance(context) }
    val forkDecisionState by forkNavRepo.forkDecisionState.collectAsState()

    val routesRepo = remember { RoutesRepository.getInstance(context) }
    val actualRouteSelectorViewModel = routeSelectorViewModel ?: remember {
        RouteSelectorViewModel(routesRepo)
    }
    val routeSelectorUiState by actualRouteSelectorViewModel.uiState.collectAsState()

    val currentLatLng by currentLocationFlow.collectAsState()
    LaunchedEffect(currentLatLng, state.userBearing, state.userSpeed) {
        currentLatLng?.let { latLng ->
            val location = Location("GPS").apply {
                latitude = latLng.latitude
                longitude = latLng.longitude
                bearing = state.userBearing
                speed = state.userSpeed
            }
            actualRouteSelectorViewModel.onLocationChanged(location)
        }
    }

    // Control the sheet state
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = false // Allow it to hide if no segment or climb
        )
    )

    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(LocalCockpitTypography provides cockpitTypography) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetShape = BottomSheetDesign.SheetShape,
            sheetContainerColor = MaterialTheme.colorScheme.surface,
            sheetShadowElevation = BottomSheetDesign.SheetShadowElevation,
            sheetTonalElevation = BottomSheetDesign.SheetTonalElevation,
        sheetDragHandle = null,
        sheetPeekHeight = if ((showLiveSegments || showLiveClimbs) && screenMode == ScreenMode.TRACKING) BottomSheetDesign.PeekHeightLiveSegment + navBarHeight else 0.dp,
        sheetSwipeEnabled = showLiveSegments || showLiveClimbs,
        sheetContent = {
            if (showLiveSegments && activeSegment != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .sheetContour()
                        .background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)
                ) {
                    LiveSegmentSheet(
                        liveSegment = activeSegment
                    )
                }
            } else if (showLiveClimbs && activeLiveClimb != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sheetContour()
                        .background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)
                ) {
                    LiveClimbSheet(
                        liveClimb = activeLiveClimb!!
                    )
                }
            } else {
                Box(Modifier
                    .fillMaxWidth()
                    .height(1.dp)) // Empty placeholder
            }
        }
    ) { paddingValues ->
        // Use a Column as the main container for the content
        // We do NOT apply the full paddingValues.bottom here because we want the
        // Map to draw UNDER the bottom sheet for a modern look.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()) // Only pad the top
        ) {
            // Pick & Place Guidance Banner (REQ-UI-200)
            if (screenMode == ScreenMode.CONFIGURATION && selectedFieldForMove != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.move_tile_banner_instruction),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { gridActions.onCancelMove() }) {
                            Text(
                                text = stringResource(R.string.move_tile_cancel),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Turn-by-Turn Navigation Prompt HUD Banner / WYSIWYG Spatial Toggle (REQ-MAP-028 / REQ-UI-275)
            if (screenMode == ScreenMode.CONFIGURATION) {
                SpatialCockpitToggleCard(
                    title = stringResource(R.string.config_tracking__show_navigation_hints),
                    isActive = state.showNavigationHints,
                    onToggle = { tabToggleActions.onToggleNavigationHints(it) },
                    icon = Icons.Default.Navigation,
                    accentColor = TTColor.RouteSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            } else {
                TurnPromptBanner(
                    navigationState = navState,
                    promptsEnabled = state.showNavigationHints && tuningConfig.turnPromptsEnabled
                )
            }

            // Return Navigation & Dynamic Elevation-Aware ETA HUD Banner (REQ-MAP-029 / ATT-1953)
            ReturnNavigationHud(
                navigationState = returnNavState,
                onDismiss = { returnNavRepo.dismissHud() }
            )

            // In-Ride Fork-in-the-Road Route Selection & Decision Alerts (REQ-MAP-031 / ATT-1955)
            ForkDecisionCard(
                decisionState = forkDecisionState,
                onRouteSelected = { routeId ->
                    forkNavRepo.selectRouteManually(routeId)
                },
                onDismiss = {
                    forkNavRepo.dismissPrompt()
                }
            )

            // Auto-Detected Route Banner (REQ-MAP-024 / ATT-1835)
            if (screenMode == ScreenMode.TRACKING && routeSelectorUiState.isAutoPromptVisible && routeSelectorUiState.autoDetectedCandidate != null) {
                routeSelectorUiState.autoDetectedCandidate?.let { candidate ->
                    AutoDetectedRouteBanner(
                        route = candidate,
                        onActivate = {
                            actualRouteSelectorViewModel.activateCandidate(candidate.summary.id)
                        },
                        onDismiss = {
                            actualRouteSelectorViewModel.dismissCandidate(candidate.summary.id)
                        }
                    )
                }
            }

            // 1. The Sensor Grid (Scrollable)
            // This Column will only take as much space as the sensors need.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (effectiveSpacing > 0.dp) Arrangement.spacedBy(effectiveSpacing) else Arrangement.Top
            ) {
                val fieldsByRow = state.fields.groupBy { it.rowNr }
                val sortedRows = fieldsByRow.keys.sorted()
                var maxRowNr = 0

                sortedRows.forEach { rowNr ->
                    maxRowNr = rowNr
                    if (screenMode == ScreenMode.CONFIGURATION) {
                        RowAdder(onClick = {
                            if (selectedFieldForMove != null) {
                                gridActions.onMoveField(selectedFieldForMove.sensorFieldId, rowNr, -1)
                            } else {
                                gridActions.onAddRow(rowNr)
                            }
                        })
                    }

                    val fieldsInThisRow = fieldsByRow[rowNr]?.sortedBy { it.colNr } ?: emptyList()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(IntrinsicSize.Min),
                        horizontalArrangement = if (effectiveSpacing > 0.dp) Arrangement.spacedBy(effectiveSpacing) else Arrangement.Start
                    ) {
                        var maxColNr = 0
                        fieldsInThisRow.forEach { fieldState ->
                            if (screenMode == ScreenMode.CONFIGURATION) {
                                ColAdder(onClick = {
                                    if (selectedFieldForMove != null) {
                                        gridActions.onMoveField(selectedFieldForMove.sensorFieldId, rowNr, fieldState.colNr)
                                    } else {
                                        gridActions.onAddCol(rowNr, fieldState.colNr)
                                    }
                                })
                            }
                            maxColNr = fieldState.colNr
                            Box(modifier = Modifier.weight(1f)) {
                                val isSelected = selectedFieldForMove?.sensorFieldId == fieldState.sensorFieldId
                                SensorFieldView(
                                    fieldState = fieldState,
                                    screenMode = screenMode,
                                    isSelectedForMove = isSelected,
                                    shape = effectiveShape,
                                    cardElevation = effectiveElevation,
                                    border = if (isSelected) {
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        effectiveBorder
                                    },
                                    onStartMove = { gridActions.onSelectFieldForMove(fieldState) },
                                    onEdit = {
                                        if (screenMode == ScreenMode.CONFIGURATION && selectedFieldForMove != null) {
                                            if (isSelected) {
                                                gridActions.onCancelMove()
                                            } else {
                                                gridActions.onSwapFields(selectedFieldForMove.sensorFieldId, fieldState.sensorFieldId)
                                            }
                                        } else {
                                            gridActions.onEditField(fieldState)
                                        }
                                    },
                                    onDelete = { gridActions.onDeleteField(fieldState) }
                                )
                            }
                        }
                        if (screenMode == ScreenMode.CONFIGURATION) {
                            ColAdder(onClick = {
                                if (selectedFieldForMove != null) {
                                    gridActions.onMoveField(selectedFieldForMove.sensorFieldId, rowNr, maxColNr + 1)
                                } else {
                                    gridActions.onAddCol(rowNr, maxColNr + 1)
                                }
                            })
                        }
                    }
                }
                if (screenMode == ScreenMode.CONFIGURATION) {
                    RowAdder(onClick = {
                        if (selectedFieldForMove != null) {
                            gridActions.onMoveField(selectedFieldForMove.sensorFieldId, maxRowNr + 1, -1)
                        } else {
                            gridActions.onAddRow(maxRowNr + 1)
                        }
                    })
                }
            }

            // Spatial WYSIWYG Toggles for Map & Elevation Profile (REQ-UI-275)
            if (screenMode == ScreenMode.CONFIGURATION) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpatialCockpitToggleCard(
                        title = stringResource(R.string.config_tracking__show_map),
                        isActive = state.showMap,
                        onToggle = { tabToggleActions.onToggleMap(it) },
                        icon = Icons.Default.Map,
                        modifier = Modifier.weight(1f)
                    )
                    SpatialCockpitToggleCard(
                        title = stringResource(R.string.config_tracking__showElevationProfile),
                        isActive = state.showElevationProfile,
                        onToggle = { tabToggleActions.onToggleElevationProfile(it) },
                        icon = Icons.Default.ShowChart,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 2. The Map (Expanded)
            // By using weight(1f) here, the Map will fill every pixel between
            // the bottom of the sensors and the bottom of the screen.
            if (state.showMap) {
                ATrainingTrackerMap(
                    zoomFocus = state.zoomFocus,
                    userBearing = state.userBearing,
                    userSpeed = state.userSpeed,
                    bSportType = state.bSportType,
                    currentLocationFlow = currentLocationFlow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Fills remaining space
                ) {
                    tracks(state.mapTracks)
                    segments(state.mapSegments, state.activeLiveSegmentIds)
                    routes(state.mapRoutes)
                    markers(state.mapMarkers)
                    liveTrack(state.currentTrack)
                }
            }

            // 3. The Elevation Profile (Below the Map)
            if (state.showElevationProfile && state.pathPoints.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    ElevationProfile(
                        pathPoints = state.pathPoints,
                        currentDistance = state.pathPoints.lastOrNull()?.distance,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Spatial WYSIWYG Dock for Live Segments, Climbs & Lap Button (REQ-UI-275)
            if (screenMode == ScreenMode.CONFIGURATION) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpatialCockpitToggleCard(
                                title = stringResource(R.string.config_tracking__showLiveSegments),
                                isActive = state.showLiveSegments,
                                onToggle = { tabToggleActions.onToggleLiveSegments(it) },
                                icon = Icons.Default.DirectionsRun,
                                modifier = Modifier.weight(1f)
                            )
                            SpatialCockpitToggleCard(
                                title = stringResource(R.string.config_tracking__show_live_climbs),
                                isActive = state.showLiveClimbs,
                                onToggle = { tabToggleActions.onToggleLiveClimbs(it) },
                                icon = Icons.Default.Terrain,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SpatialCockpitToggleCard(
                            title = stringResource(R.string.config_tracking__showLapButton),
                            isActive = state.showLapButton,
                            onToggle = { tabToggleActions.onToggleLapButton(it) },
                            icon = Icons.Default.Timer,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    }
}

/**
 * Interactive spatial toggle card for tracking cockpit HUD elements and bottom sheets (REQ-UI-275).
 * Adheres strictly to Rule 23 UI Consistency tokens: 12.dp rounded corners, 8.dp status badge,
 * primaryContainer active state, and domain semantic accents.
 */
@Composable
fun SpatialCockpitToggleCard(
    title: String,
    isActive: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color? = null
) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderStroke = when {
        isActive && accentColor != null -> BorderStroke(1.5.dp, accentColor)
        !isActive -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        else -> null
    }

    Surface(
        onClick = { onToggle(!isActive) },
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        border = borderStroke,
        tonalElevation = if (isActive) 2.dp else 0.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor ?: contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                },
                modifier = Modifier.padding(start = 6.dp)
            ) {
                Text(
                    text = if (isActive) {
                        stringResource(R.string.config_tracking__wysiwyg_active)
                    } else {
                        stringResource(R.string.config_tracking__wysiwyg_hidden)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun RowAdder(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Row",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ColAdder(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.fillMaxHeight()) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Column",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

val dummyLocationFlow = kotlinx.coroutines.flow.MutableStateFlow(
    com.google.android.gms.maps.model.LatLng(48.8566, 2.3522) // Paris, for example
)
val dummySegmentsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<LiveSegment>>(emptyList())

// Preview for Configuration Mode
@Preview(showBackground = true, name = "Config Mode")
@Composable
fun SensorGridScreenConfigPreview() {
    val context = LocalContext.current
    ATrainingTrackerTheme {
        val previewFields = listOf(
            SensorFieldState(configHash = 1, sensorFieldId = 1, rowNr = 0, colNr = 0, viewSize = ViewSize.NORMAL, label = "Pace", value = "5:31", units = "/min", zoneColor = Color.Transparent, filterDescription = "GPS: 5s avg"),
            SensorFieldState(configHash = 2, sensorFieldId = 2, rowNr = 0, colNr = 1, viewSize = ViewSize.NORMAL, label = "Heart Rate", value = "145", units = "bpm", zoneColor = TTColor.Zone1, filterDescription = ""),
            SensorFieldState(configHash = 3, sensorFieldId = 3, rowNr = 1, colNr = 0, viewSize = ViewSize.NORMAL, label = "Distance", value = "10.3", units = "km", zoneColor = Color.Transparent, filterDescription = "")
        )
        val mockActions = object : GridActions {
            override fun onEditField(fieldState: SensorFieldState) {}
            override fun onDeleteField(fieldState: SensorFieldState) {}
            override fun onAddRow(beforeRow: Int) {}
            override fun onAddCol(atRow: Int, beforeCol: Int) {}
        }
        SensorGridScreen(
            state = TrackingScreenState(fields = previewFields),
            screenMode = ScreenMode.CONFIGURATION,
            gridActions = mockActions,
            currentLocationFlow = dummyLocationFlow,
            liveSegments = dummySegmentsFlow
        )
    }
}

// Preview for Tracking Mode
@Preview(showBackground = true, name = "Tracking Mode")
@Composable
fun SensorGridScreenTrackingPreview() {
    val context = LocalContext.current
    ATrainingTrackerTheme {
        val previewFields = listOf(
            SensorFieldState(configHash = 1, sensorFieldId = 1, rowNr = 0, colNr = 0, viewSize = ViewSize.LARGE, label = "Pace", value = "5:31", units = "/min", zoneColor = Color.Transparent, filterDescription = "GPS: 5s avg"),
            SensorFieldState(configHash = 2, sensorFieldId = 2, rowNr = 0, colNr = 1, viewSize = ViewSize.LARGE, label = "Heart Rate", value = "145", zoneColor = TTColor.Zone1, units = "bpm", filterDescription = ""),
            SensorFieldState(configHash = 3, sensorFieldId = 3, rowNr = 1, colNr = 0, viewSize = ViewSize.NORMAL, label = "Distance", value = "10.3", units = "km", zoneColor = Color.Transparent, filterDescription = "")
        )
        val mockActions = object : GridActions {
            override fun onEditField(fieldState: SensorFieldState) {}
            override fun onDeleteField(fieldState: SensorFieldState) {}
            override fun onAddRow(beforeRow: Int) {}
            override fun onAddCol(atRow: Int, beforeCol: Int) {}
        }
        SensorGridScreen(
            state = TrackingScreenState(fields = previewFields),
            screenMode = ScreenMode.TRACKING,
            gridActions = mockActions,
            currentLocationFlow = dummyLocationFlow,
            liveSegments = dummySegmentsFlow
        )
    }
}