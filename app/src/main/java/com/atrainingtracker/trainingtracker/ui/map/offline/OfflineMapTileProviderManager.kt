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

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.StateFlow
import org.mapsforge.map.rendertheme.XmlRenderTheme
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes

/**
 * Process-wide singleton coordinator for the shared [MapsforgeOfflineTileProvider] (`REQ-MAP-071.2`,
 * `REQ-MAP-071.4`, `REQ-MAP-051`).
 *
 * Ensures:
 * 1. All `ATrainingTrackerMap` surfaces share one referentially stable (`===`) tile provider and
 *    one bounded `16 MiB` PNG LRU cache across 1-Hz live tracking updates.
 * 2. Zero background coroutines or `AndroidGraphicFactory.INSTANCE` accesses occur on plain-JVM /
 *    mocked contexts or when zero `.map` files are installed (Gate 3 Watchpoint 6).
 * 3. `ATT-8174` (map download/delete) and `ATT-8173` (theme switch) can hot-swap `.map` files and
 *    render themes atomically via [reload].
 */
object OfflineMapTileProviderManager {
    @Volatile
    private var providerInstance: MapsforgeOfflineTileProvider? = null

    @Volatile
    private var initialScanCompleted: Boolean = false

    /**
     * Returns the process-wide referentially stable [MapsforgeOfflineTileProvider].
     *
     * On first call on Android (`sdkInt > 0`), performs a lightweight directory scan of
     * [OfflineMapStorage] and only initializes `AndroidGraphicFactory` / opens `MultiMapDataStore`
     * if at least one valid `.map` file is present.
     */
    fun getOrCreateProvider(
        context: Context?,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): MapsforgeOfflineTileProvider {
        val existing = providerInstance
        if (existing != null && (initialScanCompleted || context == null || sdkInt <= 0)) {
            return existing
        }

        return synchronized(this) {
            val provider = providerInstance ?: MapsforgeOfflineTileProvider().also {
                providerInstance = it
            }
            if (!initialScanCompleted && context != null && sdkInt > 0) {
                initialScanCompleted = true
                val files = OfflineMapStorage.listInstalledMapFiles(context)
                if (files.isNotEmpty()) {
                    ensureGraphicsInitialized(context, sdkInt)
                    if (OfflineMapGraphicInitializer.isInitialized()) {
                        provider.reload(files, MapsforgeThemes.DEFAULT)
                    }
                }
            }
            provider
        }
    }

    /**
     * Hot-swaps the installed `.map` files and [renderTheme] across all mounted
     * `ATrainingTrackerMap` surfaces (`ATT-8174`, `ATT-8173`).
     *
     * @return Count of valid `.map` files loaded.
     */
    fun reload(
        context: Context?,
        renderTheme: XmlRenderTheme = MapsforgeThemes.DEFAULT,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Int {
        return synchronized(this) {
            val provider = providerInstance ?: MapsforgeOfflineTileProvider().also {
                providerInstance = it
            }
            initialScanCompleted = true
            val files = OfflineMapStorage.listInstalledMapFiles(context)
            if (files.isEmpty()) {
                return@synchronized provider.reload(emptyList(), renderTheme)
            }
            if (sdkInt > 0) {
                ensureGraphicsInitialized(context, sdkInt)
                if (!OfflineMapGraphicInitializer.isInitialized()) {
                    return@synchronized provider.reload(emptyList(), renderTheme)
                }
            }
            provider.reload(files, renderTheme)
        }
    }

    /** Reactive count of currently loaded valid `.map` files. */
    fun observeInstalledMapCount(
        context: Context?,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): StateFlow<Int> = getOrCreateProvider(context, sdkInt).installedMapCountFlow

    /** Monotonic revision counter bumped on every [reload] (starts at `0L`). */
    fun observeRevision(
        context: Context?,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): StateFlow<Long> = getOrCreateProvider(context, sdkInt).revisionFlow

    private fun ensureGraphicsInitialized(context: Context?, sdkInt: Int) {
        if (OfflineMapGraphicInitializer.isInitialized() || context == null) return
        val app = try {
            (context.applicationContext as? Application) ?: (context as? Application)
        } catch (_: Throwable) {
            null
        }
        OfflineMapGraphicInitializer.initialize(app, sdkInt)
    }

    @VisibleForTesting
    internal fun resetForTesting(customProvider: MapsforgeOfflineTileProvider? = null) {
        synchronized(this) {
            providerInstance?.close()
            providerInstance = customProvider
            initialScanCompleted = customProvider != null
        }
    }
}
