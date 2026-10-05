package com.atrainingtracker.trainingtracker.ui.routes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.routes.GatewayDirection

@Composable
fun GatewayFilterChipsRow(
    availableGateways: List<GatewayDirection>,
    selectedGateway: GatewayDirection,
    onGatewaySelected: (GatewayDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    if (availableGateways.size <= 1) return

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "All" chip
            FilterChip(
                selected = selectedGateway == GatewayDirection.ALL,
                onClick = { onGatewaySelected(GatewayDirection.ALL) },
                label = { Text(stringResource(GatewayDirection.ALL.titleResId)) }
            )

            // Individual direction chips
            for (gateway in availableGateways) {
                if (gateway == GatewayDirection.ALL) continue
                FilterChip(
                    selected = selectedGateway == gateway,
                    onClick = { onGatewaySelected(gateway) },
                    label = { Text(stringResource(gateway.titleResId)) }
                )
            }
        }
    }
}
