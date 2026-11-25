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

import android.util.Log
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.queue.Job
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.model.common.Observer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.XmlRenderTheme
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Pure-JVM, thread-safe, byte-bounded LRU cache for compressed PNG tile payloads
 * (`REQ-MAP-071.2`, Gate 3 Watchpoint 3).
 *
 * Avoids `android.util.LruCache` so pure-JVM tests execute under `returnDefaultValues = false`
 * without Android framework mocks.
 */
internal class ByteBoundedTileLruCache(
    val maxBytes: Int
) {
    private val map = LinkedHashMap<Long, ByteArray>(64, 0.75f, true)
    private var currentBytes: Int = 0

    init {
        require(maxBytes > 0) { "maxBytes must be positive, was $maxBytes" }
    }

    @Synchronized
    fun get(key: Long): ByteArray? = map[key]

    @Synchronized
    fun put(key: Long, value: ByteArray) {
        val entryBytes = value.size
        if (entryBytes <= 0 || entryBytes > maxBytes) {
            return
        }
        val previous = map.put(key, value)
        if (previous != null) {
            currentBytes -= previous.size
        }
        currentBytes += entryBytes
        trimToSize()
    }

    @Synchronized
    fun evictAll() {
        map.clear()
        currentBytes = 0
    }

    @Synchronized
    fun sizeBytes(): Int = currentBytes

    @Synchronized
    fun entryCount(): Int = map.size

    private fun trimToSize() {
        val iterator = map.entries.iterator()
        while (currentBytes > maxBytes && iterator.hasNext()) {
            val eldest = iterator.next()
            currentBytes -= eldest.value.size
            iterator.remove()
        }
    }
}

/**
 * Zero-memory no-op [TileCache] for Mapsforge `DatabaseRenderer` (`REQ-MAP-071.2`, Gate 3 Watchpoint 1).
 *
 * Mapsforge `0.25.0` `DatabaseRenderer` invokes `tileCache.containsKey(...)` when `renderLabels = true`,
 * so passing `null` throws a `NullPointerException` inside `executeJob`. This zero-allocation stub
 * prevents NPEs while keeping total tile cache memory strictly bounded by [ByteBoundedTileLruCache].
 */
internal object NoOpMapsforgeTileCache : TileCache {
    override fun containsKey(key: Job?): Boolean = false
    override fun destroy() = Unit
    override fun get(key: Job?): TileBitmap? = null
    override fun getCapacity(): Int = 0
    override fun getCapacityFirstLevel(): Int = 0
    override fun getImmediately(key: Job?): TileBitmap? = null
    override fun purge() = Unit
    override fun put(key: Job?, bitmap: TileBitmap?) = Unit
    override fun setWorkingSet(workingSet: MutableSet<Job>?) = Unit
    override fun addObserver(observer: Observer?) = Unit
    override fun removeObserver(observer: Observer?) = Unit
}

/**
 * Narrow internal rendering engine abstraction enabling deterministic pure-JVM concurrency,
 * cache, and hot-swap tests without live OpenGL or multi-hundred-MB `.map` fixtures.
 */
internal interface OfflineVectorTileEngine : Closeable {
    val loadedMapCount: Int
    fun supportsTile(x: Int, y: Int, zoom: Int): Boolean
    fun renderTilePng(x: Int, y: Int, zoom: Int): ByteArray?
}

/**
 * Production Mapsforge `0.25.0` rendering engine backed by
 * `MultiMapDataStore(DataPolicy.RETURN_ALL)`, `DatabaseRenderer(renderLabels = true, cacheLabels = false)`,
 * and `RenderThemeFuture` (`REQ-MAP-071.2`, Gate 3 Watchpoints 1, 2 & 4).
 */
internal class MapsforgeVectorTileEngine private constructor(
    private val multiMapDataStore: MultiMapDataStore,
    private val databaseRenderer: DatabaseRenderer,
    private val renderThemeFuture: RenderThemeFuture,
    private val displayModel: DisplayModel,
    private val tileSize: Int,
    private val textScale: Float,
    override val loadedMapCount: Int
) : OfflineVectorTileEngine {

    override fun supportsTile(x: Int, y: Int, zoom: Int): Boolean {
        if (!MapsforgeOfflineTileProvider.isValidTileCoordinate(x, y, zoom)) return false
        val mfTile = org.mapsforge.core.model.Tile(x, y, zoom.toByte(), tileSize)
        return multiMapDataStore.supportsTile(mfTile)
    }

    override fun renderTilePng(x: Int, y: Int, zoom: Int): ByteArray? {
        if (!MapsforgeOfflineTileProvider.isValidTileCoordinate(x, y, zoom)) return null
        val mfTile = org.mapsforge.core.model.Tile(x, y, zoom.toByte(), tileSize)
        val job = RendererJob(
            mfTile,
            multiMapDataStore,
            renderThemeFuture,
            displayModel,
            textScale,
            /* isTransparent = */ false,
            /* labelsOnly = */ false
        )
        val rendered: TileBitmap = databaseRenderer.executeJob(job) ?: return null
        return try {
            val out = ByteArrayOutputStream(DEFAULT_PNG_BUFFER_BYTES)
            rendered.compress(out)
            val bytes = out.toByteArray()
            if (bytes.isNotEmpty()) bytes else null
        } finally {
            rendered.decrementRefCount()
        }
    }

    override fun close() {
        try {
            renderThemeFuture.decrementRefCount()
        } catch (t: Throwable) {
            safeLogWarn("Error releasing RenderThemeFuture", t)
        }
        try {
            multiMapDataStore.close()
        } catch (t: Throwable) {
            safeLogWarn("Error closing MultiMapDataStore", t)
        }
    }

    companion object {
        private const val TAG = "MapsforgeTileEngine"
        private const val DEFAULT_PNG_BUFFER_BYTES = 16 * 1024

        /**
         * Opens all valid readable `.map` files into a single [MultiMapDataStore] using
         * [MultiMapDataStore.DataPolicy.RETURN_ALL] for seamless multi-region border stitching.
         * Corrupt or unreadable files are skipped individually without breaking valid siblings.
         */
        fun open(
            mapFiles: List<File>,
            renderTheme: XmlRenderTheme,
            tileSize: Int,
            textScale: Float
        ): MapsforgeVectorTileEngine? {
            if (mapFiles.isEmpty()) return null

            val multiStore = MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL)
            var openedCount = 0
            for (file in mapFiles) {
                if (!file.isFile || !file.canRead() || file.length() <= 0L) continue
                try {
                    val mapFile = MapFile(file)
                    multiStore.addMapDataStore(mapFile, /* useStartZoomLevel = */ true, /* useStartPosition = */ true)
                    openedCount++
                } catch (t: Throwable) {
                    safeLogWarn("Skipping corrupt or unreadable offline map file: ${file.name}", t)
                }
            }
            if (openedCount == 0) {
                try {
                    multiStore.close()
                } catch (_: Throwable) {
                    // Ignore cleanup error on empty store
                }
                return null
            }

            val displayModel = DisplayModel().apply {
                setUserScaleFactor(
                    MapsforgeOfflineTileProvider.computeNormalizedUserScaleFactor(
                        tileSize = tileSize,
                        deviceScaleFactor = DisplayModel.getDeviceScaleFactor()
                    )
                )
                setFixedTileSize(tileSize)
            }
            val graphicFactory = AndroidGraphicFactory.INSTANCE
            // Gate 3 Watchpoint 2: execute renderThemeFuture.run() synchronously before any executeJob()
            // awaits .get() so background Google Maps tile fetchers never deadlock.
            val themeFuture = RenderThemeFuture(graphicFactory, renderTheme, displayModel)
            themeFuture.run()

            // Gate 3 Watchpoint 1: standalone TileOverlay has no Mapsforge LabelLayer, so render labels
            // directly onto tiles (renderLabels = true, cacheLabels = false) with a zero-memory no-op
            // Mapsforge TileCache (avoiding DatabaseRenderer's tileCache.containsKey NPE while keeping
            // RAM strictly bounded by MapsforgeOfflineTileProvider's 16 MiB PNG LRU).
            val renderer = DatabaseRenderer(
                multiStore,
                graphicFactory,
                NoOpMapsforgeTileCache,
                /* labelStore = */ null,
                /* renderLabels = */ true,
                /* cacheLabels = */ false,
                /* hillsRenderConfig = */ null
            )

            return MapsforgeVectorTileEngine(
                multiMapDataStore = multiStore,
                databaseRenderer = renderer,
                renderThemeFuture = themeFuture,
                displayModel = displayModel,
                tileSize = tileSize,
                textScale = textScale,
                loadedMapCount = openedCount
            )
        }

        private fun safeLogWarn(message: String, t: Throwable) {
            try {
                Log.w(TAG, message, t)
            } catch (_: Throwable) {
                // Plain JVM unit test safety
            }
        }
    }
}

/**
 * Thread-safe Google Maps [TileProvider] rendering on-device Mapsforge OpenStreetMap vector `.map`
 * files beneath `ATrainingTrackerMap` overlays (`REQ-MAP-071.2`).
 *
 * Concurrency & Memory Guarantees:
 * - Bounded pure-JVM PNG LRU cache (`16 MiB` default) prevents re-rasterization across 1-Hz live
 *   tracking updates (`REQ-MAP-051`) and viewport revisits.
 * - [ReentrantReadWriteLock] coordinates concurrent Google Maps tile-fetcher threads (read lock)
 *   against atomic [reload] / [close] hot-swaps (write lock) for `ATT-8174` & `ATT-8173`.
 * - [renderLock] serializes `DatabaseRenderer.executeJob` calls and coalesces duplicate tile hits.
 * - Generation fencing prevents stale pre-reload tiles from entering the post-reload cache.
 */
class MapsforgeOfflineTileProvider internal constructor(
    val tileSize: Int = DEFAULT_TILE_SIZE,
    val textScale: Float = DEFAULT_TEXT_SCALE,
    maxCacheBytes: Int = DEFAULT_MAX_CACHE_BYTES,
    private val engineFactory: (List<File>, XmlRenderTheme, Int, Float) -> OfflineVectorTileEngine?
) : TileProvider, Closeable {

    constructor(
        tileSize: Int = DEFAULT_TILE_SIZE,
        textScale: Float = DEFAULT_TEXT_SCALE,
        maxCacheBytes: Int = DEFAULT_MAX_CACHE_BYTES
    ) : this(
        tileSize = tileSize,
        textScale = textScale,
        maxCacheBytes = maxCacheBytes,
        engineFactory = { files, theme, size, scale ->
            MapsforgeVectorTileEngine.open(files, theme, size, scale)
        }
    )

    private val stateLock = ReentrantReadWriteLock()
    private val renderLock = Any()
    private val tileCache = ByteBoundedTileLruCache(maxCacheBytes)

    private var activeEngine: OfflineVectorTileEngine? = null
    private var closed: Boolean = false
    private var generation: Long = 0L

    private val _installedMapCount = MutableStateFlow(0)
    val installedMapCountFlow: StateFlow<Int> = _installedMapCount.asStateFlow()

    private val _revision = MutableStateFlow(0L)
    val revisionFlow: StateFlow<Long> = _revision.asStateFlow()

    val installedMapCount: Int
        get() = _installedMapCount.value

    val revision: Long
        get() = _revision.value

    override fun getTile(x: Int, y: Int, zoom: Int): Tile {
        if (!isValidTileCoordinate(x, y, zoom)) {
            return TileProvider.NO_TILE
        }
        val key = packTileKey(x, y, zoom)

        return stateLock.read {
            if (closed) return@read TileProvider.NO_TILE
            val engine = activeEngine ?: return@read TileProvider.NO_TILE
            val currentGen = generation

            tileCache.get(key)?.let { cachedBytes ->
                return@read Tile(tileSize, tileSize, cachedBytes)
            }

            if (!engine.supportsTile(x, y, zoom)) {
                return@read TileProvider.NO_TILE
            }

            synchronized(renderLock) {
                if (closed || generation != currentGen) {
                    return@read TileProvider.NO_TILE
                }
                tileCache.get(key)?.let { coalescedBytes ->
                    return@read Tile(tileSize, tileSize, coalescedBytes)
                }

                val pngBytes = try {
                    engine.renderTilePng(x, y, zoom)
                } catch (t: Throwable) {
                    safeLogWarn("Tile rasterization failed for ($zoom/$x/$y)", t)
                    null
                } ?: return@read TileProvider.NO_TILE

                if (!closed && generation == currentGen) {
                    tileCache.put(key, pngBytes)
                }
                Tile(tileSize, tileSize, pngBytes)
            }
        }
    }

    /**
     * Thread-safely hot-swaps the active `.map` file set and [XmlRenderTheme], closing prior
     * file handles, evicting the tile cache, and bumping [revisionFlow] so mounted `TileOverlay`
     * instances clear Google Maps' internal tile cache (`ATT-8174`, `ATT-8173`).
     *
     * @return The number of valid `.map` files successfully loaded into `MultiMapDataStore`.
     */
    fun reload(
        mapFiles: List<File>,
        renderTheme: XmlRenderTheme = MapsforgeThemes.DEFAULT
    ): Int {
        return stateLock.write {
            if (closed) return@write 0

            val previous = activeEngine
            activeEngine = null
            generation++
            tileCache.evictAll()

            if (previous != null) {
                try {
                    previous.close()
                } catch (t: Throwable) {
                    safeLogWarn("Error closing previous offline tile engine during reload", t)
                }
            }

            val nextEngine = try {
                engineFactory(mapFiles, renderTheme, tileSize, textScale)
            } catch (t: Throwable) {
                safeLogWarn("Error opening offline tile engine during reload", t)
                null
            }

            activeEngine = nextEngine
            val loadedCount = nextEngine?.loadedMapCount ?: 0
            _installedMapCount.value = loadedCount
            _revision.value = _revision.value + 1L
            loadedCount
        }
    }

    /**
     * Thread-safely closes open `.map` handles and clears the bounded PNG cache. Subsequent
     * [getTile] calls return [TileProvider.NO_TILE].
     */
    override fun close() {
        stateLock.write {
            if (closed) return@write
            closed = true
            generation++
            tileCache.evictAll()

            val previous = activeEngine
            activeEngine = null
            _installedMapCount.value = 0

            if (previous != null) {
                try {
                    previous.close()
                } catch (t: Throwable) {
                    safeLogWarn("Error closing offline tile engine", t)
                }
            }
        }
    }

    internal fun cacheSizeBytes(): Int = tileCache.sizeBytes()

    internal fun cacheEntryCount(): Int = tileCache.entryCount()

    companion object {
        private const val TAG = "MapsforgeTileProvider"

        const val DEFAULT_TILE_SIZE: Int = 256
        const val DEFAULT_TEXT_SCALE: Float = 1.0f
        const val DEFAULT_MAX_CACHE_BYTES: Int = 16 * 1024 * 1024 // 16 MiB bounded PNG LRU
        const val MIN_ZOOM: Int = 0
        const val MAX_ZOOM: Int = 22

        /**
         * Normalizes Mapsforge's `userScaleFactor` against `DisplayModel.getDeviceScaleFactor()` so
         * `effectiveScaleFactor = deviceScaleFactor * userScaleFactor == tileSize / 256f`.
         *
         * Prevents Google Maps `TileOverlay` (which already scales tiles to `256 dp`) from double-
         * scaling Mapsforge labels and road strokes on HiDPI displays (`deviceScaleFactor > 1f`).
         */
        @JvmStatic
        fun computeNormalizedUserScaleFactor(tileSize: Int, deviceScaleFactor: Float): Float {
            val safeTileSize = tileSize.coerceAtLeast(DEFAULT_TILE_SIZE)
            val safeDeviceScale = if (deviceScaleFactor.isFinite() && deviceScaleFactor > 0.1f) {
                deviceScaleFactor
            } else {
                1.0f
            }
            return (safeTileSize.toFloat() / DEFAULT_TILE_SIZE.toFloat()) / safeDeviceScale
        }

        /**
         * Validates tile coordinates against Web Mercator bounds `[0, 2^zoom)` and supported zoom
         * range `0..22` before bit-packing or instantiating Mapsforge `Tile` (Gate 3 Watchpoint 4).
         */
        @JvmStatic
        fun isValidTileCoordinate(x: Int, y: Int, zoom: Int): Boolean {
            if (zoom !in MIN_ZOOM..MAX_ZOOM) return false
            val limit = 1 shl zoom
            return x in 0 until limit && y in 0 until limit
        }

        /**
         * Packs validated `(x, y, zoom)` into a collision-free `Long` cache key (zero String
         * allocation per tile lookup).
         */
        @JvmStatic
        fun packTileKey(x: Int, y: Int, zoom: Int): Long =
            (zoom.toLong() shl 48) or (x.toLong() shl 24) or y.toLong()

        private fun safeLogWarn(message: String, t: Throwable) {
            try {
                Log.w(TAG, message, t)
            } catch (_: Throwable) {
                // Plain JVM unit test safety
            }
        }
    }
}
