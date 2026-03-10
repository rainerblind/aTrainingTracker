package com.atrainingtracker.banalservice.ui.sporttype

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.MyHelper

@Composable
fun SportTypeScreen(
    viewModel: SportTypeViewModel
) {
    val sportTypes by viewModel.sportTypes.collectAsStateWithLifecycle()
    var itemToEdit by remember { mutableStateOf<SportTypeItem?>(null) }
    var itemToDelete by remember { mutableStateOf<SportTypeItem?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                // Create blank template for new item
                itemToEdit = SportTypeItem(-1, "", 0.0, 0.0, "", "", "", true)
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.text_new))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp) // Matching Equipment spacing
        ) {
            items(sportTypes, key = { it.id }) { item ->
                SportTypeCard(
                    item = item,
                    onClick = { itemToEdit = item },
                    onDelete = { itemToDelete = item }
                )
            }
        }
    }

    // Edit Dialog
    itemToEdit?.let { item ->
        EditSportTypeDialog(
            item = item,
            onDismiss = { itemToEdit = null },
            onConfirm = { updatedItem ->
                viewModel.saveSportType(updatedItem)
                itemToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.really_delete_workout_name_scheme, item.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSportType(item.id)
                    itemToDelete = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text(stringResource(R.string.Cancel)) }
            }
        )
    }
}

@Composable
fun SportTypeCard(
    item: SportTypeItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val speedUnit = stringResource(MyHelper.getSpeedUnitNameId())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // HEADER ZONE
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val icon = remember(item.id) {
                    SportTypeDatabaseManager.getInstance(context).getBSportTypeIcon(context, item.id, 1.0)
                }

                icon?.let {
                    Image(
                        painter = rememberDrawablePainter(it),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = stringResource(
                            R.string.average_speed_range_format,
                            MyHelper.mps2userUnit(item.minSpeed),
                            MyHelper.mps2userUnit(item.maxSpeed),
                            speedUnit
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!item.isEditable) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                } else {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // MAPPING ZONE
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Strava Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.logo_square_strava),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.mapping_format_strava, item.stravaName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // File Formats Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.sport_type_file_mapping_format,
                            stringResource(R.string.mapping_format_tcx, item.tcxName),
                            stringResource(R.string.mapping_format_gc, item.gcName)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Sport Type Card - Editable")
@Composable
fun PreviewSportTypeCardEditable() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            SportTypeCard(
                item = SportTypeItem(
                    id = 100L,
                    name = "Mountain Biking",
                    minSpeed = 2.0,
                    maxSpeed = 8.0,
                    stravaName = "MountainBike",
                    tcxName = "Biking",
                    gcName = "Mountain Bike",
                    isEditable = true
                ),
                onClick = {},
                onDelete = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Sport Type Card - System")
@Composable
fun PreviewSportTypeCardSystem() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            SportTypeCard(
                item = SportTypeItem(
                    id = 1L,
                    name = "Cycling",
                    minSpeed = 5.0,
                    maxSpeed = 15.0,
                    stravaName = "Ride",
                    tcxName = "Cycling",
                    gcName = "Bike",
                    isEditable = false
                ),
                onClick = {},
                onDelete = {}
            )
        }
    }
}

