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
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.settings.InMemoryPreferencesDataStore
import com.atrainingtracker.trainingtracker.ui.map.WorkoutBaseMapType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Robolectric verification suite for [MapBaseSource], [OfflineMapPreferences], [OfflineMapStorage],
 * [OfflineMapGraphicInitializer], and [OfflineMapTileProviderManager] (`REQ-MAP-071.1`,
 * `REQ-MAP-071.3`, `TST-MAP-074.2`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineMapPreferencesAndStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(OfflineMapPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        OfflineMapPreferences.resetForTesting()
        OfflineMapGraphicInitializer.resetForTesting()
        OfflineMapTileProviderManager.resetForTesting()
    }

    @After
    fun tearDown() {
        OfflineMapPreferences.resetForTesting()
        OfflineMapGraphicInitializer.resetForTesting()
        OfflineMapTileProviderManager.resetForTesting()
    }

    @Test
    fun mapBaseSource_safeValueOfHandlesValidCaseInsensitiveAndUnknownTokens() {
        assertEquals(MapBaseSource.GOOGLE, MapBaseSource.safeValueOf(null))
        assertEquals(MapBaseSource.GOOGLE, MapBaseSource.safeValueOf(""))
        assertEquals(MapBaseSource.GOOGLE, MapBaseSource.safeValueOf("FUTURE_VECTOR_ENGINE"))
        assertEquals(MapBaseSource.GOOGLE, MapBaseSource.safeValueOf("google"))
        assertEquals(MapBaseSource.OFFLINE_OSM, MapBaseSource.safeValueOf("OFFLINE_OSM"))
        assertEquals(MapBaseSource.OFFLINE_OSM, MapBaseSource.safeValueOf("  offline_osm "))
    }

    @Test
    fun offlineMapPreferences_persistsOrthogonallyToWorkoutBaseMapType() = runTest {
        val prefManager = MyPreferenceManager.createForTesting(context, InMemoryPreferencesDataStore())
        prefManager.setWorkoutMapBaseType(WorkoutBaseMapType.SATELLITE)

        assertEquals(MapBaseSource.GOOGLE, OfflineMapPreferences.getBaseSource(context))
        val flow = OfflineMapPreferences.observeBaseSource(context)
        assertEquals(MapBaseSource.GOOGLE, flow.value)

        // Toggle to OFFLINE_OSM and back to GOOGLE
        OfflineMapPreferences.setBaseSource(context, MapBaseSource.OFFLINE_OSM)
        assertEquals(MapBaseSource.OFFLINE_OSM, flow.value)

        // Simulate cold process restart by clearing in-memory cache and re-hydrating from SharedPreferences
        OfflineMapPreferences.resetForTesting()
        assertEquals(MapBaseSource.OFFLINE_OSM, OfflineMapPreferences.getBaseSource(context))

        // Switch back to GOOGLE and confirm WorkoutBaseMapType was preserved untouched
        OfflineMapPreferences.setBaseSource(context, MapBaseSource.GOOGLE)
        assertEquals(MapBaseSource.GOOGLE, OfflineMapPreferences.getBaseSource(context))
        assertEquals(
            "Toggling MapBaseSource MUST NOT alter WorkoutBaseMapType",
            WorkoutBaseMapType.SATELLITE,
            prefManager.workoutMapLayerConfigFlow.first().baseMapType
        )
    }

    @Test
    fun offlineMapStorage_filtersValidSortedMapFilesAndIgnoresPartials() {
        val dir = tempFolder.newFolder("offline_maps")
        File(dir, "tyrol.map").writeBytes(byteArrayOf(1, 2, 3))
        File(dir, "Bavaria.MAP").writeBytes(byteArrayOf(4, 5, 6))
        File(dir, "empty.map").createNewFile()
        File(dir, "downloading.map.tmp").writeBytes(byteArrayOf(7, 8))
        File(dir, "partial.part").writeBytes(byteArrayOf(9, 10))
        File(dir, ".hidden.map").writeBytes(byteArrayOf(11, 12))
        File(dir, "subfolder.map").mkdirs()

        val found = OfflineMapStorage.listInstalledMapFiles(dir)
        assertEquals(listOf("Bavaria.MAP", "tyrol.map"), found.map { it.name })
        assertEquals(emptyList<File>(), OfflineMapStorage.listInstalledMapFiles(null as File?))
    }

    @Test
    fun offlineMapGraphicInitializer_guardsSdkZeroAndExecutesIdempotently() {
        val app = context.applicationContext as Application
        val calls = AtomicInteger(0)

        // Plain JVM guard (sdkInt == 0) must no-op
        assertFalse(OfflineMapGraphicInitializer.initialize(app, sdkInt = 0) { calls.incrementAndGet() })
        assertFalse(OfflineMapGraphicInitializer.isInitialized())
        assertEquals(0, calls.get())

        // Null application must no-op
        assertFalse(OfflineMapGraphicInitializer.initialize(null, sdkInt = 34) { calls.incrementAndGet() })
        assertEquals(0, calls.get())

        // Defensive recovery if AndroidGraphicFactory throws
        assertFalse(OfflineMapGraphicInitializer.initialize(app, sdkInt = 34) { throw RuntimeException("boom") })
        assertFalse(OfflineMapGraphicInitializer.isInitialized())

        // Idempotent success on real Android runtime
        assertTrue(OfflineMapGraphicInitializer.initialize(app, sdkInt = 34) { calls.incrementAndGet() })
        assertTrue(OfflineMapGraphicInitializer.initialize(app, sdkInt = 34) { calls.incrementAndGet() })
        assertTrue(OfflineMapGraphicInitializer.isInitialized())
        assertEquals("Initializer must execute strictly once", 1, calls.get())
    }

    @Test
    fun offlineMapTileProviderManager_sharesReferentiallyStableInstanceAndSkipsGraphicsWhenEmpty() {
        // With 0 installed .map files, manager returns stable instance without initializing AndroidGraphicFactory
        val first = OfflineMapTileProviderManager.getOrCreateProvider(context, sdkInt = 34)
        val second = OfflineMapTileProviderManager.getOrCreateProvider(context, sdkInt = 34)
        assertSame("1-Hz tracking requires referentially identical TileProvider (REQ-MAP-051)", first, second)
        assertEquals(0, first.installedMapCount)
        assertEquals(0L, first.revision)
        assertFalse(
            "Zero installed .map files must not eagerly initialize AndroidGraphicFactory (Watchpoint 6)",
            OfflineMapGraphicInitializer.isInitialized()
        )

        // Reload on empty directory increments revision cleanly without crashing
        assertEquals(0, OfflineMapTileProviderManager.reload(context, sdkInt = 34))
        assertEquals(1L, first.revision)
    }
}
