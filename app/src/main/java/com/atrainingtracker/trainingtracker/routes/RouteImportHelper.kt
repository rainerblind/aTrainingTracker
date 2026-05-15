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

package com.atrainingtracker.trainingtracker.routes

import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

object RouteImportHelper {

    @JvmStatic
    fun importGpx(activity: FragmentActivity, uri: Uri) {
        // We use the activity's lifecycleScope so the coroutine is tied to the UI
        activity.lifecycleScope.launch {
            val importer = GpxRouteImporter(activity)

            val result = importer.importRouteFromGpx(uri)

            result.onSuccess {
                Toast.makeText(activity, "TODO: Success", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(activity, "Import failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}