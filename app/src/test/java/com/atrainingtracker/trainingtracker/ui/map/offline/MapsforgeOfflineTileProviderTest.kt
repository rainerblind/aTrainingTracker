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

import com.google.android.gms.maps.model.TileProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Pure-JVM unit & concurrency verification suite for [MapsforgeOfflineTileProvider],
 * [ByteBoundedTileLruCache], and [MapsforgeVectorTileEngine] (`REQ-MAP-071.2`, `TST-MAP-074.1`).
 */
class MapsforgeOfflineTileProviderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeTileEngine(
        override val loadedMapCount: Int,
        private val tagByte: Byte,
        private val supportedPredicate: (Int, Int, Int) -> Boolean = { _, _, _ -> true },
        private val renderDelayMs: Long = 0L
    ) : OfflineVectorTileEngine {
        val renderCalls = AtomicInteger(0)
        val closed = AtomicBoolean(false)
        val inFlight = AtomicInteger(0)
        val maxConcurrentRenders = AtomicInteger(0)

        override fun supportsTile(x: Int, y: Int, zoom: Int): Boolean {
            if (closed.get()) throw IllegalStateException("supportsTile called on closed engine")
            return supportedPredicate(x, y, zoom)
        }

        override fun renderTilePng(x: Int, y: Int, zoom: Int): ByteArray? {
            if (closed.get()) throw IllegalStateException("renderTilePng called on closed engine")
            val current = inFlight.incrementAndGet()
            maxConcurrentRenders.accumulateAndGet(current) { prev, next -> maxOf(prev, next) }
            try {
                if (renderDelayMs > 0L) Thread.sleep(renderDelayMs)
                renderCalls.incrementAndGet()
                return byteArrayOf(tagByte, zoom.toByte(), (x and 0xFF).toByte(), (y and 0xFF).toByte())
            } finally {
                inFlight.decrementAndGet()
            }
        }

        override fun close() {
            closed.set(true)
        }
    }

    @Test
    fun tileCoordinateValidation_rejectsOutOfBoundsAndAcceptsValidCorners() {
        assertTrue(MapsforgeOfflineTileProvider.isValidTileCoordinate(0, 0, 0))
        assertFalse(MapsforgeOfflineTileProvider.isValidTileCoordinate(1, 0, 0))
        assertFalse(MapsforgeOfflineTileProvider.isValidTileCoordinate(0, 1, 0))
        assertFalse(MapsforgeOfflineTileProvider.isValidTileCoordinate(-1, 0, 10))
        assertFalse(MapsforgeOfflineTileProvider.isValidTileCoordinate(0, -1, 10))
        assertFalse(MapsforgeOfflineTileProvider.isValidTileCoordinate(0, 0, -1))
        assertFalse(MapsforgeOfflineTileProvider.isValidTileCoordinate(0, 0, 23))

        val maxZ14 = (1 shl 14) - 1
        assertTrue(MapsforgeOfflineTileProvider.isValidTileCoordinate(maxZ14, maxZ14, 14))
        assertFalse(MapsforgeOfflineTileProvider.isValidTileCoordinate(maxZ14 + 1, 0, 14))

        val maxZ22 = (1 shl 22) - 1
        assertTrue(MapsforgeOfflineTileProvider.isValidTileCoordinate(maxZ22, maxZ22, 22))
        assertNotEquals(
            MapsforgeOfflineTileProvider.packTileKey(100, 200, 14),
            MapsforgeOfflineTileProvider.packTileKey(200, 100, 14)
        )
        assertNotEquals(
            MapsforgeOfflineTileProvider.packTileKey(100, 200, 14),
            MapsforgeOfflineTileProvider.packTileKey(100, 200, 15)
        )
    }

    @Test
    fun byteBoundedLruCache_evictsLeastRecentlyUsedWithinCeiling() {
        val cache = ByteBoundedTileLruCache(maxBytes = 10)
        cache.put(1L, ByteArray(4) { 1 })
        cache.put(2L, ByteArray(4) { 2 })
        assertEquals(8, cache.sizeBytes())
        assertEquals(2, cache.entryCount())

        // Touch key 1 so key 2 becomes LRU
        assertNotNull(cache.get(1L))
        cache.put(3L, ByteArray(4) { 3 })

        assertTrue("Cache must stay <= maxBytes", cache.sizeBytes() <= 10)
        assertNotNull("Recently accessed key 1 must remain", cache.get(1L))
        assertNull("LRU key 2 must be evicted", cache.get(2L))
        assertNotNull("Newly inserted key 3 must remain", cache.get(3L))

        // Oversized single entry must be ignored without clearing valid entries
        cache.put(99L, ByteArray(20))
        assertNull(cache.get(99L))
        assertEquals(8, cache.sizeBytes())

        cache.evictAll()
        assertEquals(0, cache.sizeBytes())
        assertEquals(0, cache.entryCount())
    }

    @Test
    fun getTile_returnsNoTileWhenEmptyOrUnsupportedOrOutOfBounds() {
        val fake = FakeTileEngine(
            loadedMapCount = 1,
            tagByte = 1,
            supportedPredicate = { x, y, z -> z == 12 && x == 2170 && y == 1420 }
        )
        val provider = MapsforgeOfflineTileProvider(engineFactory = { _, _, _, _ -> fake })

        // Before any reload
        assertSame(TileProvider.NO_TILE, provider.getTile(2170, 1420, 12))

        assertEquals(1, provider.reload(listOf(tempFolder.newFile("region.map"))))
        assertEquals(1, provider.installedMapCount)
        assertEquals(1L, provider.revision)

        // Unsupported tile -> NO_TILE without rasterizing
        assertSame(TileProvider.NO_TILE, provider.getTile(10, 10, 12))
        // Invalid coordinate -> NO_TILE without rasterizing
        assertSame(TileProvider.NO_TILE, provider.getTile(-1, 1420, 12))
        assertSame(TileProvider.NO_TILE, provider.getTile(0, 0, 25))
        assertEquals(0, fake.renderCalls.get())

        // Supported tile -> rendered once, cached on repeat (REQ-MAP-051)
        val first = provider.getTile(2170, 1420, 12)
        val second = provider.getTile(2170, 1420, 12)
        assertEquals(256, first.width)
        assertEquals(256, first.height)
        assertArrayEquals(byteArrayOf(1, 12, (2170 and 0xFF).toByte(), (1420 and 0xFF).toByte()), first.data)
        assertArrayEquals(first.data, second.data)
        assertEquals("Second fetch must hit PNG LRU cache without re-rasterizing", 1, fake.renderCalls.get())

        provider.close()
        assertTrue(fake.closed.get())
        assertEquals(0, provider.installedMapCount)
        assertEquals(0, provider.cacheEntryCount())
        assertSame(TileProvider.NO_TILE, provider.getTile(2170, 1420, 12))
    }

    @Test
    fun reload_closesOldEngineEvictsCacheAndBumpsRevision() {
        val engines = mutableListOf<FakeTileEngine>()
        val generationTag = AtomicInteger(1)
        val provider = MapsforgeOfflineTileProvider(
            engineFactory = { files, _, _, _ ->
                if (files.isEmpty()) null
                else FakeTileEngine(files.size, generationTag.getAndIncrement().toByte()).also { engines.add(it) }
            }
        )

        val mapA = tempFolder.newFile("bavaria.map")
        val mapB = tempFolder.newFile("tyrol.map")

        assertEquals(1, provider.reload(listOf(mapA)))
        val tileV1 = provider.getTile(100, 200, 14)
        assertEquals(1.toByte(), tileV1.data!![0])
        assertEquals(1L, provider.revision)
        assertEquals(1, provider.cacheEntryCount())

        // Hot-swap with two adjacent regional maps
        assertEquals(2, provider.reload(listOf(mapA, mapB), MapsforgeThemes.DEFAULT))
        assertTrue("Previous engine must be closed on reload", engines[0].closed.get())
        assertEquals(2, provider.installedMapCount)
        assertEquals(2L, provider.revision)
        assertEquals("Cache must be evicted on reload", 0, provider.cacheEntryCount())

        val tileV2 = provider.getTile(100, 200, 14)
        assertEquals(2.toByte(), tileV2.data!![0])

        // Hot-swap to empty list falls back cleanly
        assertEquals(0, provider.reload(emptyList()))
        assertTrue(engines[1].closed.get())
        assertEquals(0, provider.installedMapCount)
        assertEquals(3L, provider.revision)
        assertSame(TileProvider.NO_TILE, provider.getTile(100, 200, 14))
        provider.close()
    }

    @Test
    fun concurrentGetTileAndHotSwapReloadAndClose_zeroRacesAndSerializedRenderer() {
        val genCounter = AtomicInteger(1)
        val createdEngines = java.util.Collections.synchronizedList(mutableListOf<FakeTileEngine>())
        val provider = MapsforgeOfflineTileProvider(
            engineFactory = { files, _, _, _ ->
                if (files.isEmpty()) null
                else FakeTileEngine(
                    loadedMapCount = files.size,
                    tagByte = genCounter.getAndIncrement().toByte(),
                    renderDelayMs = 1L
                ).also { createdEngines.add(it) }
            }
        )
        val dummyFile = tempFolder.newFile("alps.map")
        provider.reload(listOf(dummyFile))

        val failure = AtomicReference<Throwable?>(null)
        val stopSignal = AtomicBoolean(false)
        val readersCount = 6
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(readersCount + 1)
        val pool = Executors.newFixedThreadPool(readersCount + 1)

        repeat(readersCount) { readerIdx ->
            pool.submit {
                try {
                    startLatch.await(2, TimeUnit.SECONDS)
                    var iter = 0
                    while (!stopSignal.get()) {
                        val tile = provider.getTile(1000 + (iter % 8), 2000 + readerIdx, 14)
                        if (tile !== TileProvider.NO_TILE) {
                            assertEquals(256, tile.width)
                            assertNotNull(tile.data)
                        }
                        iter++
                    }
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        pool.submit {
            try {
                startLatch.await(2, TimeUnit.SECONDS)
                repeat(15) {
                    provider.reload(listOf(dummyFile))
                    Thread.sleep(2L)
                }
                provider.close()
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            } finally {
                stopSignal.set(true)
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        assertTrue("Concurrency stress test timed out", doneLatch.await(15, TimeUnit.SECONDS))
        pool.shutdownNow()

        assertNull("Expected zero exceptions under concurrent getTile/reload/close", failure.get())
        assertTrue("Every created engine must be closed after close()", createdEngines.all { it.closed.get() })
        assertTrue(
            "renderLock must serialize DatabaseRenderer calls (max concurrency <= 1)",
            createdEngines.all { it.maxConcurrentRenders.get() <= 1 }
        )
        assertSame(TileProvider.NO_TILE, provider.getTile(1000, 2000, 14))
    }

    @Test
    fun normalizedUserScaleFactor_cancelsDoubleHiDpiScaling() {
        val pixel8Scale = MapsforgeOfflineTileProvider.computeNormalizedUserScaleFactor(256, 2.625f)
        assertEquals(1.0f, pixel8Scale * 2.625f, 1e-4f)

        val hidpi512Scale = MapsforgeOfflineTileProvider.computeNormalizedUserScaleFactor(512, 3.0f)
        assertEquals(2.0f, hidpi512Scale * 3.0f, 1e-4f)

        assertEquals(1.0f, MapsforgeOfflineTileProvider.computeNormalizedUserScaleFactor(256, 0f), 1e-4f)
        assertEquals(1.0f, MapsforgeOfflineTileProvider.computeNormalizedUserScaleFactor(256, Float.NaN), 1e-4f)
    }

    @Test
    fun noOpMapsforgeTileCache_holdsZeroMemoryAndNeverThrows() {
        assertFalse(NoOpMapsforgeTileCache.containsKey(null))
        assertNull(NoOpMapsforgeTileCache.get(null))
        assertNull(NoOpMapsforgeTileCache.getImmediately(null))
        assertEquals(0, NoOpMapsforgeTileCache.getCapacity())
        assertEquals(0, NoOpMapsforgeTileCache.getCapacityFirstLevel())
        NoOpMapsforgeTileCache.put(null, null)
        NoOpMapsforgeTileCache.purge()
        NoOpMapsforgeTileCache.destroy()
        NoOpMapsforgeTileCache.setWorkingSet(null)
        NoOpMapsforgeTileCache.addObserver(null)
        NoOpMapsforgeTileCache.removeObserver(null)
    }

    @Test
    fun mapsforgeVectorTileEngine_skipsCorruptAndEmptyMapFilesSafely() {
        val emptyFile = tempFolder.newFile("empty.map")
        val corruptFile = tempFolder.newFile("corrupt.map").apply {
            writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05))
        }
        val missingFile = File(tempFolder.root, "does_not_exist.map")

        val engine = MapsforgeVectorTileEngine.open(
            mapFiles = listOf(emptyFile, corruptFile, missingFile),
            renderTheme = MapsforgeThemes.DEFAULT,
            tileSize = 256,
            textScale = 1.0f
        )
        assertNull("Corrupt/empty/missing files must gracefully yield null engine without throwing", engine)
    }
}
