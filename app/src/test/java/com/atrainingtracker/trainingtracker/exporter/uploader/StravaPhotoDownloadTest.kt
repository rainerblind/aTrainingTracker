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

package com.atrainingtracker.trainingtracker.exporter.uploader

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutPhotoManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaPhotoPreferences
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.StravaActivityParser
import com.atrainingtracker.trainingtracker.ui.aftermath.StravaActivityPhoto
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.TimeZone

/**
 * Unit and Robolectric SQLite tests for Strava API v3 activity photo download,
 * settings preference, storage, EXIF/coordinate resolution, deduplication, and
 * privacy guard (`ATT-8100`, `REQ-EXP-050`, `TST-EXP-050`).
 */
// Linked test specs: TST-EXP-050
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StravaPhotoDownloadTest {

    private lateinit var context: Context
    private val capturedRequests = mutableListOf<Request>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setStaticField(TrainingApplication::class.java, "cAppContext", context)
        setStaticField(
            TrainingApplication::class.java,
            "cSharedPreferences",
            PreferenceManager.getDefaultSharedPreferences(context)
        )
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        OkHttpClient()
        capturedRequests.clear()
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        StravaUploadDbHelper.resetForTesting()
        WorkoutRepository.resetForTesting(null)
        SportTypesRepository.resetForTesting(null)
    }

    @After
    fun tearDown() {
        unmockkAll()
        WorkoutRepository.resetForTesting(null)
        SportTypesRepository.resetForTesting(null)
        StravaUploadDbHelper.resetForTesting()
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        setStaticField(
            TrainingApplication::class.java,
            "cSharedPreferences",
            PreferenceManager.getDefaultSharedPreferences(context)
        )
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        val field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    private fun createJpegBytes(width: Int = 16, height: Int = 16, color: Int = android.graphics.Color.BLUE): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun createJpegBytesWithExif(
        exifDateTime: String? = null,
        lat: Double? = null,
        lng: Double? = null,
        color: Int = android.graphics.Color.RED
    ): ByteArray {
        val tempFile = File.createTempFile("exif_seed_", ".jpg", context.cacheDir)
        return try {
            tempFile.writeBytes(createJpegBytes(color = color))
            val exif = ExifInterface(tempFile.absolutePath)
            if (exifDateTime != null) {
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifDateTime)
            }
            if (lat != null && lng != null) {
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, toDmsRational(kotlin.math.abs(lat)))
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (lat >= 0) "N" else "S")
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, toDmsRational(kotlin.math.abs(lng)))
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (lng >= 0) "E" else "W")
            }
            exif.saveAttributes()
            tempFile.readBytes()
        } finally {
            tempFile.delete()
        }
    }

    private fun toDmsRational(decimalDegrees: Double): String {
        val deg = decimalDegrees.toInt()
        val minFloat = (decimalDegrees - deg) * 60.0
        val min = minFloat.toInt()
        val sec = ((minFloat - min) * 60.0 * 1000.0).toInt()
        return "$deg/1,$min/1,$sec/1000"
    }

    private fun seedWorkoutWithTrack(
        fileBaseName: String = "2026-11-25_070000",
        startTimeDb: String = "2026-11-25 07:00:00",
        trackLat: Double = 48.1351,
        trackLng: Double = 11.5820
    ): Long {
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context)
        val cv = ContentValues().apply {
            put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName)
            put(WorkoutSummaries.WORKOUT_NAME, "Morning Test Ride")
            put(WorkoutSummaries.TIME_START, startTimeDb)
            put(WorkoutSummaries.TIME_TOTAL_s, 3600)
            put(WorkoutSummaries.TIME_ACTIVE_s, 3500)
            put(WorkoutSummaries.SPORT_ID, 1L)
            put(WorkoutSummaries.B_SPORT, BSportType.BIKE.name)
            put(WorkoutSummaries.FINISHED, 1)
        }
        val workoutId = summariesDb.database.insert(WorkoutSummaries.TABLE, null, cv)
        assertTrue("Seeded workoutId must be positive", workoutId > 0L)

        val samplesMgr = WorkoutSamplesDatabaseManager.getInstance(context)
        val tableName = WorkoutSamplesDatabaseManager.getTableName(fileBaseName)
        samplesMgr.database.execSQL(
            "CREATE TABLE IF NOT EXISTS $tableName (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "time TEXT, " +
                "${SensorType.LATITUDE.name} REAL, " +
                "${SensorType.LONGITUDE.name} REAL)"
        )
        val s1 = ContentValues().apply {
            put("time", "2026-11-25 07:00:00")
            put(SensorType.LATITUDE.name, trackLat)
            put(SensorType.LONGITUDE.name, trackLng)
        }
        val s2 = ContentValues().apply {
            put("time", "2026-11-25 07:10:00")
            put(SensorType.LATITUDE.name, trackLat + 0.02)
            put(SensorType.LONGITUDE.name, trackLng + 0.04)
        }
        samplesMgr.database.insert(tableName, null, s1)
        samplesMgr.database.insert(tableName, null, s2)
        return workoutId
    }

    @Test
    fun testDownloadStravaPhotos_preferenceDefaultsToFalseAndIsNullSafe() {
        assertFalse("downloadStravaPhotos must default to false", StravaPhotoPreferences.isDownloadEnabled(context))

        StravaPhotoPreferences.setDownloadEnabled(context, true)
        assertTrue(StravaPhotoPreferences.isDownloadEnabled(context))

        StravaPhotoPreferences.setDownloadEnabled(context, false)
        assertFalse(StravaPhotoPreferences.isDownloadEnabled(context))

        // Null-safe Context guard
        assertFalse(StravaPhotoPreferences.isDownloadEnabled(null))
        StravaPhotoPreferences.setDownloadEnabled(null, true) // must not throw NPE
    }

    @Test
    fun testStravaActivityParser_parseActivityPhotos_extractsBestUrlTimestampAndValidCoordinates() {
        val json = JSONArray(
            """
            [
              {
                "unique_id": "abc-123",
                "caption": "Summit view",
                "created_at": "2026-11-25T07:05:00Z",
                "location": [48.1234, 11.5678],
                "urls": {
                  "100": "https://cdn.example.com/100.jpg",
                  "600": "https://cdn.example.com/600.jpg",
                  "2048": "https://cdn.example.com/2048.jpg"
                }
              },
              {
                "unique_id": "null-island-photo",
                "created_at": "2026-11-25T07:06:00Z",
                "location": [0.0, 0.0],
                "urls": {
                  "0": "https://cdn.example.com/original.png"
                }
              },
              {
                "unique_id": "unsafe-url",
                "urls": {
                  "600": "file:///etc/passwd"
                }
              }
            ]
            """.trimIndent()
        )

        val photos = StravaActivityParser.parseActivityPhotos(json)
        assertEquals(2, photos.size)

        assertEquals("abc-123", photos[0].uniqueId)
        assertEquals("https://cdn.example.com/2048.jpg", photos[0].url)
        assertEquals("Summit view", photos[0].caption)
        assertNotNull(photos[0].createdAtMs)
        assertEquals(48.1234, photos[0].latitude!!, 1e-5)
        assertEquals(11.5678, photos[0].longitude!!, 1e-5)

        assertEquals("null-island-photo", photos[1].uniqueId)
        assertEquals("https://cdn.example.com/original.png", photos[1].url)
        assertNull("Null-Island (0.0, 0.0) must be rejected", photos[1].latitude)
        assertNull("Null-Island (0.0, 0.0) must be rejected", photos[1].longitude)
    }

    @Test
    fun testStravaActivityParser_parsePhotosSummaryFallbackAndZeroHint() {
        val zeroActivity = JSONObject("""{"id": 1, "total_photo_count": 0, "photo_count": 0}""")
        assertTrue(StravaActivityParser.hasZeroPhotosHint(zeroActivity))

        val minimalActivity = JSONObject("""{"id": 1}""")
        assertFalse(StravaActivityParser.hasZeroPhotosHint(minimalActivity))

        val withSummary = JSONObject(
            """
            {
              "id": 1,
              "total_photo_count": 1,
              "photos": {
                "count": 1,
                "primary": {
                  "unique_id": "primary-1",
                  "urls": { "600": "https://cdn.example.com/primary600.jpg" }
                }
              }
            }
            """.trimIndent()
        )
        assertFalse(StravaActivityParser.hasZeroPhotosHint(withSummary))
        val parsed = StravaActivityParser.parsePhotosSummary(withSummary)
        assertEquals(1, parsed.size)
        assertEquals("primary-1", parsed[0].uniqueId)
        assertEquals("https://cdn.example.com/primary600.jpg", parsed[0].url)

        // REQ-EXP-013 privacy preservation: minimize still discards photos
        val minimized = JSONObject(StravaActivityParser.minimize(withSummary)!!)
        assertFalse(minimized.has("photos"))
        assertFalse(minimized.has("total_photo_count"))
    }

    @Test
    fun testStravaUploader_whenPhotoDownloadDisabled_skipsPhotosApiAndDiscardsPhotoData() {
        val fileBaseName = "2026-11-25_070000"
        val workoutId = seedWorkoutWithTrack(fileBaseName = fileBaseName)

        StravaPhotoPreferences.setDownloadEnabled(context, false)
        mockkStatic(StravaHelper::class)
        every { StravaHelper.getRefreshedAccessToken() } returns "oauth_token_123"

        val mockSportTypesRepo = mockk<SportTypesRepository>(relaxed = true)
        every { mockSportTypesRepo.getStravaName(any()) } returns "Ride"
        SportTypesRepository.resetForTesting(mockSportTypesRepo)

        StravaUploadDbHelper.getInstance(context).updateActivityId(fileBaseName, "555001")

        val testClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedRequests.add(req)
                val url = req.url.toString()
                val body = when {
                    url == "https://www.strava.com/api/v3/activities/555001" -> {
                        """
                        {
                          "id": 555001,
                          "type": "Ride",
                          "sport_type": "Ride",
                          "total_photo_count": 2,
                          "photos": {
                            "count": 2,
                            "primary": {
                              "unique_id": "p1",
                              "urls": { "600": "https://cdn.example.com/p1.jpg" }
                            }
                          }
                        }
                        """.trimIndent()
                    }
                    else -> "{}"
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val uploader = StravaUploader(context, testClient).apply { sleepStrategy = {} }
        val result = uploader.doUpdate(ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY), isDuplicate = false)

        assertTrue(result.success())
        assertTrue(
            "Zero /photos requests must be made when setting is disabled",
            capturedRequests.none { it.url.toString().contains("/photos") || it.url.host.contains("cdn.example.com") }
        )
        assertEquals(0, WorkoutPhotoManager.getPhotosForWorkout(context, workoutId).size)

        val storedJson = JSONObject(StravaUploadDbHelper.getInstance(context).getStravaActivityData(fileBaseName)!!)
        assertFalse(storedJson.has("photos"))
        assertFalse(storedJson.has("total_photo_count"))
    }

    @Test
    fun testStravaUploader_whenPhotoDownloadEnabled_fetchesDownloadsInterpolatesAndDeduplicates() {
        val fileBaseName = "2026-11-25_070000"
        val workoutId = seedWorkoutWithTrack(fileBaseName = fileBaseName)

        StravaPhotoPreferences.setDownloadEnabled(context, true)
        mockkStatic(StravaHelper::class)
        every { StravaHelper.getRefreshedAccessToken() } returns "oauth_token_123"

        val mockSportTypesRepo = mockk<SportTypesRepository>(relaxed = true)
        every { mockSportTypesRepo.getStravaName(any()) } returns "Ride"
        SportTypesRepository.resetForTesting(mockSportTypesRepo)

        StravaUploadDbHelper.getInstance(context).updateActivityId(fileBaseName, "777002")

        val jpegPhoto1 = createJpegBytesWithExif(
            exifDateTime = "2026:11:25 07:02:00",
            lat = 48.5000,
            lng = 11.9000,
            color = android.graphics.Color.GREEN
        )
        // Photo 2 has no EXIF and no API location -> must interpolate at 07:05:00 UTC (midpoint between 07:00 and 07:10!)
        val jpegPhoto2 = createJpegBytes(color = android.graphics.Color.YELLOW)

        val testClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedRequests.add(req)
                val url = req.url.toString()
                when {
                    url == "https://www.strava.com/api/v3/activities/777002" -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"id": 777002, "type": "Ride", "sport_type": "Ride", "total_photo_count": 2}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    url.startsWith("https://www.strava.com/api/v3/activities/777002/photos") -> {
                        assertEquals("Bearer oauth_token_123", req.header("Authorization"))
                        val photosPayload = """
                            [
                              {
                                "unique_id": "strava-uuid-1",
                                "caption": "Lake stop",
                                "created_at": "2026-11-25T07:02:00Z",
                                "urls": {
                                  "600": "https://cdn.example.com/p1_600.jpg",
                                  "2048": "https://cdn.example.com/p1_2048.jpg"
                                }
                              },
                              {
                                "unique_id": "strava-uuid-2",
                                "caption": "Midpoint",
                                "created_at": "2026-11-25T07:05:00Z",
                                "location": [0.0, 0.0],
                                "urls": {
                                  "2048": "https://cdn.example.com/p2_2048.jpg"
                                }
                              }
                            ]
                        """.trimIndent()
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(photosPayload.toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    url == "https://cdn.example.com/p1_2048.jpg" -> {
                        assertNull("CDN image request must NOT send Strava Bearer token", req.header("Authorization"))
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(jpegPhoto1.toResponseBody("image/jpeg".toMediaType()))
                            .build()
                    }
                    url == "https://cdn.example.com/p2_2048.jpg" -> {
                        assertNull("CDN image request must NOT send Strava Bearer token", req.header("Authorization"))
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(jpegPhoto2.toResponseBody("image/jpeg".toMediaType()))
                            .build()
                    }
                    else -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .body("".toResponseBody(null))
                            .build()
                    }
                }
            }
            .build()

        val uploader = StravaUploader(context, testClient).apply { sleepStrategy = {} }
        val result1 = uploader.doUpdate(ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY), isDuplicate = true)
        assertTrue(result1.success())

        val storedPhotos = WorkoutPhotoManager.getPhotosForWorkout(context, workoutId)
        assertEquals(2, storedPhotos.size)

        val expectedDir = WorkoutPhotoManager.getWorkoutPhotosDir(context, workoutId)
        assertTrue(storedPhotos.all { File(it.filePath).parentFile?.absolutePath == expectedDir.absolutePath })
        assertTrue(storedPhotos.all { File(it.filePath).exists() })

        val photo1 = storedPhotos.first { it.caption == "Lake stop" }
        assertEquals(48.5000, photo1.latitude!!, 1e-4)
        assertEquals(11.9000, photo1.longitude!!, 1e-4)

        val photo2 = storedPhotos.first { it.caption == "Midpoint" }
        // Midpoint between (48.1351, 11.5820) @ 07:00:00 and (48.1551, 11.6220) @ 07:10:00 -> (48.1451, 11.6020)
        assertNotNull("Interpolated latitude must be populated", photo2.latitude)
        assertNotNull("Interpolated longitude must be populated", photo2.longitude)
        assertEquals(48.1451, photo2.latitude!!, 1e-3)
        assertEquals(11.6020, photo2.longitude!!, 1e-3)

        // Re-running sync must deduplicate pre-download and dispatch ZERO CDN image requests
        capturedRequests.clear()
        val result2 = uploader.doUpdate(ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY), isDuplicate = true)
        assertTrue(result2.success())
        assertEquals(2, WorkoutPhotoManager.getPhotosForWorkout(context, workoutId).size)
        assertTrue(
            "Repeat sync must not re-download existing Strava photos from CDN",
            capturedRequests.none { it.url.host == "cdn.example.com" }
        )
    }

    @Test
    fun testWorkoutPhotoManager_importStravaPhotos_deduplicatesBulkImportCameraTimestampAndSha256() {
        val workoutId = seedWorkoutWithTrack(fileBaseName = "2026-11-25_080000")
        val dir = WorkoutPhotoManager.getWorkoutPhotosDir(context, workoutId)
        val db = WorkoutSummariesDatabaseManager.getInstance(context)

        // 1. Existing bulk-imported photo (<uniqueId>.jpg)
        val bulkFile = File(dir, "bulk-uuid-1.jpg").apply { writeBytes(createJpegBytes(color = android.graphics.Color.CYAN)) }
        db.insertPhoto(workoutId, bulkFile.absolutePath, 1_700_000_010_000L, 48.1, 11.5, "Bulk")

        // 2. Existing local camera photo with capture timestamp
        val cameraFile = File(dir, "photo_1700000100000_cam1.jpg").apply { writeBytes(createJpegBytes(color = android.graphics.Color.MAGENTA)) }
        db.insertPhoto(workoutId, cameraFile.absolutePath, 1_700_000_100_000L, 48.2, 11.6, "Camera")

        // 3. Existing local gallery photo with identical SHA-256 bytes to Strava photo 3 (but different timestamp & id)
        val sharedBytes = createJpegBytes(width = 24, height = 24, color = android.graphics.Color.DKGRAY)
        val galleryFile = File(dir, "photo_1700000200000_gal1.jpg").apply { writeBytes(sharedBytes) }
        db.insertPhoto(workoutId, galleryFile.absolutePath, 1_700_000_200_000L, 48.3, 11.7, "Gallery")

        val newBytes = createJpegBytes(width = 32, height = 32, color = android.graphics.Color.WHITE)

        val cdnRequestedUrls = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                cdnRequestedUrls.add(url)
                val bodyBytes = when (url) {
                    "https://cdn.example.com/same_sha.jpg" -> sharedBytes
                    "https://cdn.example.com/brand_new.jpg" -> newBytes
                    else -> createJpegBytes()
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bodyBytes.toResponseBody("image/jpeg".toMediaType()))
                    .build()
            }
            .build()

        val candidates = listOf(
            // Matches bulk-uuid-1.jpg by filename -> skipped pre-download
            StravaActivityPhoto("bulk-uuid-1", "https://cdn.example.com/bulk.jpg", createdAtMs = 1_700_000_010_000L),
            // Matches camera photo takenAtMs within 2000 ms -> skipped pre-download
            StravaActivityPhoto("cam-dup-id", "https://cdn.example.com/cam.jpg", createdAtMs = 1_700_000_101_000L),
            // Different id & timestamp, identical SHA-256 -> skipped post-download
            StravaActivityPhoto("sha-dup-id", "https://cdn.example.com/same_sha.jpg", createdAtMs = 1_700_000_500_000L),
            // Genuinely new photo -> imported!
            StravaActivityPhoto("new-strava-id", "https://cdn.example.com/brand_new.jpg", caption = "New Strava Shot", createdAtMs = 1_700_000_600_000L)
        )

        val imported = WorkoutPhotoManager.importStravaPhotos(context, workoutId, candidates, client)
        assertEquals(1, imported)
        assertEquals(4, WorkoutPhotoManager.getPhotosForWorkout(context, workoutId).size)
        assertFalse("Bulk-imported photo must be skipped pre-download", cdnRequestedUrls.contains("https://cdn.example.com/bulk.jpg"))
        assertFalse("Timestamp-matched camera photo must be skipped pre-download", cdnRequestedUrls.contains("https://cdn.example.com/cam.jpg"))
        assertTrue(WorkoutPhotoManager.getPhotosForWorkout(context, workoutId).any { it.caption == "New Strava Shot" })
    }

    @Test
    fun testWorkoutPhotoManager_importStravaPhotos_preservesMultipleDistinctPhotosWithoutTimestampOrExif() {
        val workoutId = seedWorkoutWithTrack(fileBaseName = "2026-11-25_090000")
        val bytesA = createJpegBytes(width = 16, height = 16, color = android.graphics.Color.RED)
        val bytesB = createJpegBytes(width = 16, height = 16, color = android.graphics.Color.BLUE)

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val bytes = if (url.endsWith("a.jpg")) bytesA else bytesB
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bytes.toResponseBody("image/jpeg".toMediaType()))
                    .build()
            }
            .build()

        val candidates = listOf(
            StravaActivityPhoto("multi-1", "https://cdn.example.com/a.jpg", caption = "First", createdAtMs = null),
            StravaActivityPhoto("multi-2", "https://cdn.example.com/b.jpg", caption = "Second", createdAtMs = null)
        )

        val imported = WorkoutPhotoManager.importStravaPhotos(context, workoutId, candidates, client)
        assertEquals(2, imported)
        assertEquals(2, WorkoutPhotoManager.getPhotosForWorkout(context, workoutId).size)
    }

    @Test
    fun testStravaUploader_photosEndpointFailureFallsBackToPhotosSummaryAndRemainsNonFatal() {
        val fileBaseName = "2026-11-25_100000"
        val workoutId = seedWorkoutWithTrack(fileBaseName = fileBaseName)

        StravaPhotoPreferences.setDownloadEnabled(context, true)
        mockkStatic(StravaHelper::class)
        every { StravaHelper.getRefreshedAccessToken() } returns "oauth_token_123"

        val mockSportTypesRepo = mockk<SportTypesRepository>(relaxed = true)
        every { mockSportTypesRepo.getStravaName(any()) } returns "Ride"
        SportTypesRepository.resetForTesting(mockSportTypesRepo)

        StravaUploadDbHelper.getInstance(context).updateActivityId(fileBaseName, "888003")

        val summaryJpeg = createJpegBytes(color = android.graphics.Color.LTGRAY)

        val testClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                when {
                    url == "https://www.strava.com/api/v3/activities/888003" -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(
                                """
                                {
                                  "id": 888003,
                                  "type": "Ride",
                                  "sport_type": "Ride",
                                  "total_photo_count": 1,
                                  "photos": {
                                    "count": 1,
                                    "primary": {
                                      "unique_id": "summary-fallback-1",
                                      "urls": { "600": "https://cdn.example.com/summary600.jpg" }
                                    }
                                  }
                                }
                                """.trimIndent().toResponseBody("application/json".toMediaType())
                            )
                            .build()
                    }
                    url.startsWith("https://www.strava.com/api/v3/activities/888003/photos") -> {
                        // Simulate API endpoint failure -> fallback to photos_summary
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(500)
                            .message("Internal Server Error")
                            .body("{}".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    url == "https://cdn.example.com/summary600.jpg" -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(summaryJpeg.toResponseBody("image/jpeg".toMediaType()))
                            .build()
                    }
                    else -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .body("".toResponseBody(null))
                            .build()
                    }
                }
            }
            .build()

        val uploader = StravaUploader(context, testClient).apply { sleepStrategy = {} }
        val result = uploader.doUpdate(ExportInfo(fileBaseName, FileFormat.STRAVA, ExportType.COMMUNITY), isDuplicate = true)

        assertTrue("doUpdate must succeed even if /photos returned 500", result.success())
        val stored = WorkoutPhotoManager.getPhotosForWorkout(context, workoutId)
        assertEquals(1, stored.size)
        assertTrue(stored[0].filePath.contains("strava_summary-fallback-1"))
    }

    @Test
    fun `parser rejects cleartext http urls while keeping https urls`() {
        val photosJson = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("unique_id", "cleartext-photo")
                    put("urls", JSONObject().apply { put("600", "http://cdn.strava.com/insecure.jpg") })
                }
            )
            put(
                JSONObject().apply {
                    put("unique_id", "https-photo")
                    put("urls", JSONObject().apply { put("600", "https://cdn.strava.com/secure.jpg") })
                }
            )
        }

        val parsed = StravaActivityParser.parseActivityPhotos(photosJson)
        assertEquals(1, parsed.size)
        assertEquals("https-photo", parsed[0].uniqueId)
        assertEquals("https://cdn.strava.com/secure.jpg", parsed[0].url)

        val summaryActivityJson = JSONObject().apply {
            put(
                "photos",
                JSONObject().apply {
                    put(
                        "primary",
                        JSONObject().apply {
                            put("unique_id", "cleartext-summary")
                            put("urls", JSONObject().apply { put("600", "http://cdn.strava.com/summary.jpg") })
                        }
                    )
                }
            )
        }
        assertTrue(
            "parsePhotosSummary must reject cleartext http:// URLs",
            StravaActivityParser.parsePhotosSummary(summaryActivityJson).isEmpty()
        )
    }

    @Test
    fun `importStravaPhotos rejects non-https urls without network call`() {
        val workoutId = seedWorkoutWithTrack()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                capturedRequests.add(chain.request())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(createJpegBytes().toResponseBody("image/jpeg".toMediaType()))
                    .build()
            }
            .build()

        val imported = WorkoutPhotoManager.importStravaPhotos(
            context = context,
            workoutId = workoutId,
            photos = listOf(
                StravaActivityPhoto(
                    uniqueId = "http-only-photo",
                    url = "http://cdn.strava.com/insecure.jpg",
                ),
                StravaActivityPhoto(
                    uniqueId = "file-scheme-photo",
                    url = "file:///etc/passwd",
                ),
            ),
            httpClient = client,
        )

        assertEquals(0, imported)
        assertTrue("No HTTP requests may be issued for non-HTTPS URLs", capturedRequests.isEmpty())
        assertTrue(WorkoutPhotoManager.getPhotosForWorkout(context, workoutId).isEmpty())
    }

    @Test
    fun `importStravaPhotos rejects path traversal uniqueId values without network call`() {
        val workoutId = seedWorkoutWithTrack()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                capturedRequests.add(chain.request())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(createJpegBytes().toResponseBody("image/jpeg".toMediaType()))
                    .build()
            }
            .build()

        val imported = WorkoutPhotoManager.importStravaPhotos(
            context = context,
            workoutId = workoutId,
            photos = listOf(
                StravaActivityPhoto(
                    uniqueId = "../../etc/evil",
                    url = "https://cdn.strava.com/1.jpg",
                ),
                StravaActivityPhoto(
                    uniqueId = "sub/dir",
                    url = "https://cdn.strava.com/2.jpg",
                ),
                StravaActivityPhoto(
                    uniqueId = "..",
                    url = "https://cdn.strava.com/3.jpg",
                ),
            ),
            httpClient = client,
        )

        assertEquals(0, imported)
        assertTrue("No HTTP requests may be issued for unsafe uniqueIds", capturedRequests.isEmpty())
        assertTrue(WorkoutPhotoManager.getPhotosForWorkout(context, workoutId).isEmpty())
    }

    @Test
    fun `importStravaPhotos rejects non-image content types and cleans temp files`() {
        val workoutId = seedWorkoutWithTrack()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedRequests.add(req)
                val isHtml = req.url.encodedPath.contains("html")
                val contentType = if (isHtml) "text/html; charset=utf-8" else "application/json"
                val bodyPayload = if (isHtml) "<html>not an image</html>" else "{\"error\":\"nope\"}"
                val builder = Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bodyPayload.toResponseBody(contentType.toMediaType()))
                if (isHtml) {
                    builder.header("Content-Type", contentType)
                }
                builder.build()
            }
            .build()

        val imported = WorkoutPhotoManager.importStravaPhotos(
            context = context,
            workoutId = workoutId,
            photos = listOf(
                StravaActivityPhoto(
                    uniqueId = "html-response",
                    url = "https://cdn.strava.com/html.jpg",
                ),
                StravaActivityPhoto(
                    uniqueId = "json-response",
                    url = "https://cdn.strava.com/json.jpg",
                ),
            ),
            httpClient = client,
        )

        assertEquals(2, capturedRequests.size)
        assertEquals(0, imported)
        assertTrue(WorkoutPhotoManager.getPhotosForWorkout(context, workoutId).isEmpty())
        val files = WorkoutPhotoManager.getWorkoutPhotosDir(context, workoutId).listFiles().orEmpty()
        assertTrue("No partial or non-image files may remain in workout photos dir", files.isEmpty())
    }
}
