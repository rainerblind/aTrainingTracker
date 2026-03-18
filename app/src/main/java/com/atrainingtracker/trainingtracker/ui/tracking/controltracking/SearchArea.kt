package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atrainingtracker.R

@Composable
fun SearchArea(
    searchingFor: String?,  // The name of the device we are currently searching for; null when not searching.
    onSearch: () -> Unit
) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (searchingFor == null) {
            // STATE: IDLE - Show the "No Shape" clickable icon/text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable(
                        onClick = onSearch
                    )
                    .padding(8.dp) // Some extra padding for a better touch target
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.research_icon),
                    contentDescription = null,
                    modifier = Modifier.size(53.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.research),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        else {
            // STATE: SEARCHING - Show progress and device name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp)) // Space between circle and text
                Text(
                    text = stringResource(id = R.string.searching_for_device_format, searchingFor),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Idle State")
@Composable
fun PreviewSearchAreaIdle() {
    MaterialTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            SearchArea(
                searchingFor = null,
                onSearch = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Searching State")
@Composable
fun PreviewSearchAreaSearching() {
    MaterialTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            SearchArea(
                searchingFor = "Some Device",
                onSearch = {}
            )
        }
    }
}