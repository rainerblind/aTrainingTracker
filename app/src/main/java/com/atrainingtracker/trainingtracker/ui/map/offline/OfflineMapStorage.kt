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

package com.atrainingtracker.trainingtracker.ui.map.offline

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * Canonical app-private directory & file-discovery helper for offline Mapsforge `.map` files
 * (`REQ-MAP-071.2`).
 *
 * Uses app-private external files storage (`getExternalFilesDir(null)/offline_maps`, falling back
 * to `filesDir/offline_maps`) requiring zero broad storage permissions and directly consumable by
 * `ATT-8174` (Download/SAF Import Manager) and `ATT-8176` (Pre-Ride Route Coverage Verification).
 */
object OfflineMapStorage {
    const val OFFLINE_MAPS_DIR_NAME = "offline_maps"
    const val MAP_FILE_EXTENSION = ".map"

    /**
     * Resolves the app-private offline maps directory safely (returns `null` on unmocked/empty
     * JVM test contexts per `Coding Standards §5.5`).
     */
    @JvmStatic
    fun getMapsDirectory(context: Context?, createIfMissing: Boolean = false): File? {
        if (context == null) return null
        val baseDir = try {
            val appCtx = context.applicationContext ?: context
            appCtx.getExternalFilesDir(null) ?: appCtx.filesDir
        } catch (_: Throwable) {
            null
        } ?: return null

        if (baseDir.path.isBlank()) return null

        val mapsDir = File(baseDir, OFFLINE_MAPS_DIR_NAME)
        if (createIfMissing && !mapsDir.exists()) {
            try {
                mapsDir.mkdirs()
            } catch (_: Throwable) {
                // Non-fatal; caller inspects existence/readability
            }
        }
        return mapsDir
    }

    /**
     * Lists all readable, non-empty `*.map` files in [directory], deterministically sorted by
     * case-insensitive filename while ignoring partial downloads (`.tmp`, `.part`), hidden files,
     * subdirectories, and zero-byte placeholders.
     */
    @JvmStatic
    fun listInstalledMapFiles(directory: File?): List<File> {
        if (directory == null || !directory.exists() || !directory.isDirectory) {
            return emptyList()
        }
        val children = try {
            directory.listFiles()
        } catch (_: Throwable) {
            null
        } ?: return emptyList()

        return children
            .asSequence()
            .filter { file ->
                file.isFile &&
                    !file.name.startsWith(".") &&
                    file.name.endsWith(MAP_FILE_EXTENSION, ignoreCase = true) &&
                    file.canRead() &&
                    file.length() > 0L
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .toList()
    }

    /** Convenience overload resolving [getMapsDirectory] for [context]. */
    @JvmStatic
    fun listInstalledMapFiles(context: Context?): List<File> =
        listInstalledMapFiles(getMapsDirectory(context, createIfMissing = false))
}
