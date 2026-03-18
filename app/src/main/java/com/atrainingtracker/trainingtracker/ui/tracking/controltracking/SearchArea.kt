package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

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
    Row(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        // The Search/Research Button
        OutlinedButton(
            onClick = onSearch,
            enabled = searchingFor != null, // Disable while searching
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            // Using a Column inside the button to put text BELOW the image
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.research_icon),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(id = R.string.research),
                    fontSize = 10.sp
                )
            }
        }

        if (searchingFor != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.searching_for_device_format, searchingFor),
                    style = MaterialTheme.typography.headlineSmall
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