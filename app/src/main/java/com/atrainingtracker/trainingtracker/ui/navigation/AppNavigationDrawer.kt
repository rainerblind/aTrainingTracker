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

package com.atrainingtracker.trainingtracker.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * Configuration model representing a single item in the navigation drawer.
 *
 * @property id Android resource ID associated with the navigation destination (e.g. `R.id.drawer_start_tracking`).
 * @property iconRes Drawable resource ID for the item's leading icon.
 * @property titleRes String resource ID for the item's localized display label.
 */
data class DrawerItemConfig(
    val id: Int,
    val iconRes: Int,
    val titleRes: Int
)

/**
 * Configuration model representing a logical section/group of items in the navigation drawer.
 *
 * @property titleRes String resource ID for the category section header.
 * @property items List of [DrawerItemConfig] entries belonging to this category.
 */
data class DrawerGroup(
    val titleRes: Int,
    val items: List<DrawerItemConfig>
)

/**
 * Controller holding reactive state for [AppNavigationDrawer].
 *
 * Exposes observable state properties that trigger Compose recomposition when mutated from Java or Kotlin.
 *
 * @property selectedItemId Currently selected navigation item ID.
 * @property startTrackingTitleRes String resource ID for the top tracking drawer item ("Start", "Tracking", or "Pause").
 */
class NavigationDrawerController(
    initialSelectedItemId: Int = R.id.drawer_start_tracking,
    initialStartTrackingTitleRes: Int = R.string.tab_start
) {
    var selectedItemId: Int by mutableIntStateOf(initialSelectedItemId)
    var startTrackingTitleRes: Int by mutableIntStateOf(initialStartTrackingTitleRes)
}

/**
 * Declarative Jetpack Compose Navigation Drawer replacing the legacy `NavigationView`.
 *
 * Delivers compact item density (ATT-243), deterministic non-reflective UI layout (ATT-516),
 * and reactive tracking state updates.
 *
 * @param selectedItemId ID of the currently selected destination.
 * @param startTrackingTitleRes Localized string resource ID for the dynamic tracking state label.
 * @param onItemSelected Callback invoked when a navigation drawer row is selected.
 */
@Composable
fun AppNavigationDrawer(
    selectedItemId: Int,
    startTrackingTitleRes: Int,
    onItemSelected: (Int) -> Unit
) {
    val groups = listOf(
        DrawerGroup(
            titleRes = R.string.drawer__training,
            items = listOf(
                DrawerItemConfig(R.id.drawer_start_tracking, R.drawable.control_start, startTrackingTitleRes),
                DrawerItemConfig(R.id.drawer_workouts, R.drawable.workout_list, R.string.tab_workouts),
                DrawerItemConfig(R.id.drawer_periods, R.drawable.ic_calendar_month, R.string.workout_periods__periods)
            )
        ),
        DrawerGroup(
            titleRes = R.string.drawer__maps,
            items = listOf(
                DrawerItemConfig(R.id.drawer_map, R.drawable.ic_map, R.string.tab_map),
                DrawerItemConfig(R.id.drawer_segments, R.drawable.ic_segment, R.string.segments),
                DrawerItemConfig(R.id.drawer_routes, R.drawable.ic_route, R.string.routes),
                DrawerItemConfig(R.id.drawer_my_locations, R.drawable.my_locations, R.string.my_locations)
            )
        ),
        DrawerGroup(
            titleRes = R.string.drawer__my_stuff,
            items = listOf(
                DrawerItemConfig(R.id.drawer_my_sensors, R.drawable.ic_my_paired_devices, R.string.devices_myRemoteDevices),
                DrawerItemConfig(R.id.drawer_bikes, R.drawable.ic_equipment_bike, R.string.prefs_manage_bikes_title),
                DrawerItemConfig(R.id.drawer_shoes, R.drawable.ic_equipment_shoe, R.string.prefs_manage_shoes_title),
                DrawerItemConfig(R.id.drawer_sport_types, R.drawable.ic_sports_combined, R.string.sport_types),
                DrawerItemConfig(R.id.drawer_training_zones, R.drawable.ic_zones_hr_run_combined, R.string.prefs_training_zones)
            )
        ),
        DrawerGroup(
            titleRes = R.string.prefsOnlineCommunities,
            items = listOf(
                DrawerItemConfig(R.id.drawer_strava, R.drawable.logo_square_strava, R.string.Strava),
                DrawerItemConfig(R.id.drawer_dropbox, R.drawable.dropbox_logo_blue, R.string.Dropbox),
                DrawerItemConfig(R.id.drawer_export, R.drawable.ic_upload, R.string.prefsExportTitle)
            )
        ),
        DrawerGroup(
            titleRes = R.string.drawer__settings,
            items = listOf(
                DrawerItemConfig(R.id.drawer_units, R.drawable.ic_square_foot, R.string.prefsUnitsTitle),
                DrawerItemConfig(R.id.drawer_display_settings, R.drawable.ic_display_settings, R.string.Display),
                DrawerItemConfig(R.id.drawer_tracking_layouts, R.drawable.ic_table_edit, R.string.prefsConfigureDisplaysTitle),
                DrawerItemConfig(R.id.drawer_search_settings, R.drawable.ic_search, R.string.Search_Settings),
                DrawerItemConfig(R.id.drawer_backup_restore, R.drawable.ic_save_to_disc, R.string.import_backup),
                DrawerItemConfig(R.id.drawer_privacy_policy, R.drawable.ic_privacy, R.string.privacy_policy)
            )
        )
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            DrawerHeader()

            groups.forEachIndexed { index, group ->
                DrawerGroupView(
                    group = group,
                    selectedItemId = selectedItemId,
                    onItemSelected = onItemSelected
                )
                if (index < groups.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                }
            }
            Spacer(modifier = Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

/**
 * Top header displaying application branding, icon, and title over the landscape background image.
 */
@Composable
fun DrawerHeader() {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val statusBarHeight = if (topInset > 0.dp) topInset else 36.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp + statusBarHeight)
    ) {
        Image(
            painter = painterResource(id = R.drawable.menu_header_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = statusBarHeight + 8.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_512),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart
            )
            Text(
                text = stringResource(id = R.string.TrainingTracker),
                color = colorResource(id = R.color.my_blue),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Section view rendering a category header and its child navigation rows.
 */
@Composable
fun DrawerGroupView(
    group: DrawerGroup,
    selectedItemId: Int,
    onItemSelected: (Int) -> Unit
) {
    Column {
        Text(
            text = stringResource(id = group.titleRes),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
        )
        group.items.forEach { item ->
            DrawerItemView(
                item = item,
                isSelected = selectedItemId == item.id,
                onClick = { onItemSelected(item.id) }
            )
        }
    }
}

/**
 * Single navigation item row configured for compact density (ATT-243: 40dp height).
 */
@Composable
fun DrawerItemView(
    item: DrawerItemConfig,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp) // ATT-243: Denser items
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val context = LocalContext.current
        val drawable = remember(item.iconRes) {
            ContextCompat.getDrawable(context, item.iconRes)
        }
        if (drawable != null) {
            Image(
                painter = rememberDrawablePainter(drawable),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(32.dp))
        Text(
            text = stringResource(id = item.titleRes),
            fontSize = 14.sp,
            color = contentColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * Interoperability helper method to bind the Compose navigation drawer to a [ComposeView].
 *
 * Configures the [ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed] strategy
 * and attaches the root theme and composable tree.
 *
 * @param composeView The host ComposeView from the activity layout.
 * @param controller State controller containing selected item and tracking status state.
 * @param onItemSelected Callback invoked with the selected item's resource ID.
 */
fun setupComposeNavigationDrawer(
    composeView: ComposeView,
    controller: NavigationDrawerController,
    onItemSelected: (Int) -> Unit
) {
    composeView.apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ATrainingTrackerTheme {
                AppNavigationDrawer(
                    selectedItemId = controller.selectedItemId,
                    startTrackingTitleRes = controller.startTrackingTitleRes,
                    onItemSelected = onItemSelected
                )
            }
        }
    }
}
