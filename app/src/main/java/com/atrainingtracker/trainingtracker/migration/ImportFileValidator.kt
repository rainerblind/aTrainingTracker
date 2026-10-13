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

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Defensive file format and extension validator for workout import pickers (ATT-2622 / REQ-UI-294).
 */
object ImportFileValidator {

    /**
     * Checks whether the provided [fileName] matches the [expectedFormat] (e.g., "fit", "tcx", "gpx").
     *
     * If [fileName] is null, blank, or has no file extension (such as opaque content provider URIs),
     * this returns true to allow the file to proceed to content-level stream inspection in the engine.
     * If an extension is present, it is verified case-insensitively against [expectedFormat].
     */
    fun isMatchingFormat(fileName: String?, expectedFormat: String): Boolean {
        if (fileName.isNullOrBlank()) return true
        val dotIndex = fileName.lastIndexOf('.')
        if (dotIndex == -1 || dotIndex == fileName.length - 1) {
            return true
        }
        val extension = fileName.substring(dotIndex + 1).trim()
        if (extension.isEmpty()) return true
        return extension.equals(expectedFormat.trim(), ignoreCase = true)
    }

    /**
     * Resolves the display name of a [Uri] via [OpenableColumns.DISPLAY_NAME], returning null if unavailable.
     */
    fun resolveDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) cursor.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
