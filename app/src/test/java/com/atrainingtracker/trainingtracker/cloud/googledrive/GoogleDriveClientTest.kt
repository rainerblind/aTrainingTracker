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

package com.atrainingtracker.trainingtracker.cloud.googledrive

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests verifying GoogleDriveClient REST calls, folder resolution, multipart upload,
 * download streaming, and auth header injection.
 */
class GoogleDriveClientTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        GoogleDriveClient.clearFolderCache()
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun tearDown() {
        GoogleDriveClient.clearFolderCache()
        unmockkStatic(Log::class)
    }

    @Test
    fun testAuthHeaderInjection_addsBearerToken() {
        var capturedAuthHeader: String? = null

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                capturedAuthHeader = chain.request().header("Authorization")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"files":[]}""".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val client = GoogleDriveClient(
            tokenProvider = { "secret_test_token_xyz" },
            client = okHttpClient
        )

        client.findFolderIdByName("test_folder", "root")
        assertEquals("Bearer secret_test_token_xyz", capturedAuthHeader)
    }

    @Test
    fun testEnsureFolderHierarchy_resolvesExistingAndCreatesMissing() {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()

                val responseBody = when {
                    url.contains("files?q=") && url.contains("aTrainingTracker") -> {
                        // aTrainingTracker exists
                        """{"files":[{"id":"folder_att_id","name":"aTrainingTracker"}]}"""
                    }
                    url.contains("files?q=") && url.contains("Workouts") -> {
                        // Workouts does not exist initially
                        """{"files":[]}"""
                    }
                    request.method == "POST" && url.endsWith("/drive/v3/files") -> {
                        // creation of Workouts folder
                        """{"id":"folder_workouts_id","name":"Workouts"}"""
                    }
                    else -> """{"files":[]}"""
                }

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseBody.toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val client = GoogleDriveClient(
            tokenProvider = { "test_token" },
            client = okHttpClient
        )

        val folderId = client.ensureFolderHierarchy(listOf("aTrainingTracker", "Workouts"))
        assertEquals("folder_workouts_id", folderId)

        // Second call should return cached value without network query
        val cachedFolderId = client.ensureFolderHierarchy(listOf("aTrainingTracker", "Workouts"))
        assertEquals("folder_workouts_id", cachedFolderId)
    }

    @Test
    fun testUploadOrOverwriteFile_createsMultipartWhenFileDoesNotExist() {
        var uploadMethod: String? = null
        var uploadUrl: String? = null

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()

                if (url.contains("files?q=")) {
                    // findFileIdByName returns empty -> file doesn't exist yet
                    return@addInterceptor Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"files":[]}""".toResponseBody("application/json".toMediaTypeOrNull()))
                        .build()
                }

                if (url.contains("uploadType=multipart")) {
                    uploadMethod = request.method
                    uploadUrl = url
                    return@addInterceptor Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"id":"uploaded_file_id"}""".toResponseBody("application/json".toMediaTypeOrNull()))
                        .build()
                }

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()

        val client = GoogleDriveClient(tokenProvider = { "token" }, client = okHttpClient)
        val file = tempFolder.newFile("test_workout.fit").apply {
            writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        }

        val result = client.uploadOrOverwriteFile("parent_folder", "test_workout.fit", "application/vnd.ant.fit", file)
        assertTrue(result)
        assertEquals("POST", uploadMethod)
        assertTrue(uploadUrl?.contains("uploadType=multipart") == true)
    }

    @Test
    fun testUploadOrOverwriteFile_updatesMediaWhenFileAlreadyExists() {
        var patchMethod: String? = null
        var patchUrl: String? = null

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()

                if (url.contains("files?q=")) {
                    // findFileIdByName returns existing file id
                    return@addInterceptor Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"files":[{"id":"existing_file_123"}]}""".toResponseBody("application/json".toMediaTypeOrNull()))
                        .build()
                }

                if (url.contains("uploadType=media")) {
                    patchMethod = request.method
                    patchUrl = url
                    return@addInterceptor Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"id":"existing_file_123"}""".toResponseBody("application/json".toMediaTypeOrNull()))
                        .build()
                }

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()

        val client = GoogleDriveClient(tokenProvider = { "token" }, client = okHttpClient)
        val file = tempFolder.newFile("backup.attbackup").apply {
            writeText("dummy backup data")
        }

        val result = client.uploadOrOverwriteFile("parent_folder", "backup.attbackup", "application/octet-stream", file)
        assertTrue(result)
        assertEquals("PATCH", patchMethod)
        assertTrue(patchUrl?.contains("existing_file_123") == true)
    }

    @Test
    fun testDownloadFile_streamsBytesToDestination() {
        val payload = "google drive downloaded backup content".toByteArray()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()

                if (url.contains("files?q=")) {
                    return@addInterceptor Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"files":[{"id":"download_target_id"}]}""".toResponseBody("application/json".toMediaTypeOrNull()))
                        .build()
                }

                if (url.contains("download_target_id?alt=media")) {
                    return@addInterceptor Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(payload.toResponseBody("application/octet-stream".toMediaTypeOrNull()))
                        .build()
                }

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()

        val client = GoogleDriveClient(tokenProvider = { "token" }, client = okHttpClient)
        val destinationFile = File(tempFolder.root, "restored.attbackup")

        val result = client.downloadFile("parent_folder", "backup.attbackup", destinationFile)
        assertTrue(result)
        assertTrue(destinationFile.exists())
        assertEquals("google drive downloaded backup content", destinationFile.readText())
    }

    @Test
    fun test401Unauthorized_invokesTokenRefresherAndRetriesSuccessfully() {
        var callCount = 0
        var tokenRefresherCalled = false
        val capturedAuthHeaders = mutableListOf<String?>()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callCount++
                capturedAuthHeaders.add(chain.request().header("Authorization"))

                if (callCount == 1) {
                    // Initial call with expired token returns 401 Unauthorized
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(401)
                        .message("Unauthorized")
                        .body("""{"error":{"message":"Invalid Credentials","code":401}}""".toResponseBody("application/json".toMediaTypeOrNull()))
                        .build()
                } else {
                    // Retry with refreshed token returns 200 OK
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"files":[{"id":"recovered_folder_id","name":"Workouts"}]}""".toResponseBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
            }
            .build()

        val client = GoogleDriveClient(
            tokenProvider = { "expired_token_123" },
            client = okHttpClient,
            tokenRefresher = {
                tokenRefresherCalled = true
                "fresh_token_456"
            }
        )

        val folderId = client.findFolderIdByName("Workouts", "root")

        assertTrue("Expected tokenRefresher to be invoked", tokenRefresherCalled)
        assertEquals(2, callCount)
        assertEquals("Bearer expired_token_123", capturedAuthHeaders[0])
        assertEquals("Bearer fresh_token_456", capturedAuthHeaders[1])
        assertEquals("recovered_folder_id", folderId)
        assertEquals(200, client.lastHttpCode)
        assertNull(client.lastErrorMessage)
    }

    @Test
    fun testCreateFolder_whenParentIsRoot_omitsParentsField() {
        var capturedPayload: String? = null

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val buffer = okio.Buffer()
                request.body?.writeTo(buffer)
                capturedPayload = buffer.readUtf8()

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"id":"new_root_folder_id","name":"aTrainingTracker"}""".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val client = GoogleDriveClient(tokenProvider = { "token" }, client = okHttpClient)
        val folderId = client.createFolder("aTrainingTracker", "root")

        assertEquals("new_root_folder_id", folderId)
        assertNotNull(capturedPayload)
        val json = org.json.JSONObject(capturedPayload!!)
        assertEquals("aTrainingTracker", json.getString("name"))
        assertFalse("Root folder payload MUST NOT contain 'parents' field", json.has("parents"))
    }

    @Test
    fun testCreateFolder_whenParentIsNotRoot_includesParentsField() {
        var capturedPayload: String? = null

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val buffer = okio.Buffer()
                request.body?.writeTo(buffer)
                capturedPayload = buffer.readUtf8()

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"id":"child_folder_id","name":"Workouts"}""".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val client = GoogleDriveClient(tokenProvider = { "token" }, client = okHttpClient)
        val folderId = client.createFolder("Workouts", "parent_att_123")

        assertEquals("child_folder_id", folderId)
        assertNotNull(capturedPayload)
        val json = org.json.JSONObject(capturedPayload!!)
        assertEquals("Workouts", json.getString("name"))
        assertTrue("Subfolder payload MUST contain 'parents' field", json.has("parents"))
        assertEquals("parent_att_123", json.getJSONArray("parents").getString(0))
    }

    @Test
    fun testCaptureDiagnostics_onFailureStoresCodeAndErrorMessage() {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(403)
                    .message("Forbidden")
                    .body("""{"error":{"message":"Storage quota exceeded","code":403}}""".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val client = GoogleDriveClient(tokenProvider = { "token" }, client = okHttpClient)
        val result = client.findFolderIdByName("Workouts", "root")

        assertNull(result)
        assertEquals(403, client.lastHttpCode)
        assertNotNull(client.lastErrorMessage)
        assertTrue(client.lastErrorMessage!!.contains("Storage quota exceeded"))
    }

    @Test
    fun testEnsureFolderHierarchy_concurrentCalls_sharesGlobalCacheWithoutDuplicateCreations() {
        var createCount = 0
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.method == "POST") {
                    createCount++
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"id":"created_folder_id","name":"test"}""".toResponseBody("application/json".toMediaTypeOrNull()))
                        .build()
                } else {
                    // Not found on search
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"files":[]}""".toResponseBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
            }
            .build()

        val client1 = GoogleDriveClient(tokenProvider = { "token" }, client = okHttpClient)
        val client2 = GoogleDriveClient(tokenProvider = { "token" }, client = okHttpClient)

        val id1 = client1.ensureFolderHierarchy(listOf("test"))
        val id2 = client2.ensureFolderHierarchy(listOf("test"))

        assertEquals("created_folder_id", id1)
        assertEquals("created_folder_id", id2)
        assertEquals("Should create folder only once across separate client instances", 1, createCount)
    }

    @Test
    fun testGoogleDriveQueryBuilder_escapesQuotesAndBackslashes() {
        val escaped1 = GoogleDriveQueryBuilder.escapeQueryValue("Runner's Track")
        assertEquals("Runner\\'s Track", escaped1)

        val escaped2 = GoogleDriveQueryBuilder.escapeQueryValue("Workouts\\2026")
        assertEquals("Workouts\\\\2026", escaped2)

        val escaped3 = GoogleDriveQueryBuilder.escapeQueryValue("O'Connor's \\Special\\")
        assertEquals("O\\'Connor\\'s \\\\Special\\\\", escaped3)

        val folderQuery = GoogleDriveQueryBuilder.buildFolderQuery("Runner's Track", "parent'1")
        assertEquals("name = 'Runner\\'s Track' and 'parent\\'1' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false", folderQuery)

        val fileQuery = GoogleDriveQueryBuilder.buildFileQuery("Bob's Workout.fit", "folder'2")
        assertEquals("name = 'Bob\\'s Workout.fit' and 'folder\\'2' in parents and trashed = false", fileQuery)

        val childrenQuery = GoogleDriveQueryBuilder.buildChildrenQuery("folder'3")
        assertEquals("'folder\\'3' in parents and trashed = false", childrenQuery)
    }

    @Test
    fun testEnsureFolderHierarchy_withSpecialCharacters_executesEscapedQuery() {
        var capturedQueryUrl: String? = null

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                capturedQueryUrl = request.url.toString()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"files":[{"id":"special_folder_id","name":"Runner's Track"}]}""".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val client = GoogleDriveClient(
            tokenProvider = { "token" },
            client = okHttpClient
        )

        val folderId = client.findFolderIdByName("Runner's Track", "root")
        assertEquals("special_folder_id", folderId)
        assertNotNull(capturedQueryUrl)
        // URL decode to verify query contents
        val decodedUrl = java.net.URLDecoder.decode(capturedQueryUrl!!, "UTF-8")
        assertTrue("Query must contain escaped single quote: $decodedUrl", decodedUrl.contains("name = 'Runner\\'s Track'"))
    }

    @Test
    fun testGoogleDriveFolderCache_accountIsolation() {
        var createCountUser1 = 0
        var createCountUser2 = 0

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val auth = request.header("Authorization")
                val isUser1 = auth?.contains("token_user_1") == true

                if (request.method == "POST") {
                    if (isUser1) createCountUser1++ else createCountUser2++
                    val folderId = if (isUser1) "folder_id_user_1" else "folder_id_user_2"
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"id":"$folderId","name":"Workouts"}""".toResponseBody("application/json".toMediaTypeOrNull()))
                        .build()
                } else {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"files":[]}""".toResponseBody("application/json".toMediaTypeOrNull()))
                        .build()
                }
            }
            .build()

        val clientUser1 = GoogleDriveClient(
            tokenProvider = { "token_user_1" },
            client = okHttpClient,
            accountProvider = { "user1@example.com" }
        )
        val clientUser2 = GoogleDriveClient(
            tokenProvider = { "token_user_2" },
            client = okHttpClient,
            accountProvider = { "user2@example.com" }
        )

        val id1 = clientUser1.ensureFolderHierarchy(listOf("Workouts"))
        val id2 = clientUser2.ensureFolderHierarchy(listOf("Workouts"))

        assertEquals("folder_id_user_1", id1)
        assertEquals("folder_id_user_2", id2)
        assertEquals(1, createCountUser1)
        assertEquals(1, createCountUser2)

        // Subsequent lookup must use respective account's cached ID
        assertEquals("folder_id_user_1", clientUser1.ensureFolderHierarchy(listOf("Workouts")))
        assertEquals("folder_id_user_2", clientUser2.ensureFolderHierarchy(listOf("Workouts")))
    }

    @Test
    fun testEvictFolder_atomicallyClearsMatchingEntries() {
        var searchCount = 0
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                searchCount++
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"files":[{"id":"folder_to_evict","name":"Workouts"}]}""".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val client = GoogleDriveClient(
            tokenProvider = { "token" },
            client = okHttpClient
        )

        // Initial resolution caches the folder
        val idFirst = client.ensureFolderHierarchy(listOf("Workouts"))
        assertEquals("folder_to_evict", idFirst)
        assertEquals(1, searchCount)

        // Second call hits cache
        val idCached = client.ensureFolderHierarchy(listOf("Workouts"))
        assertEquals("folder_to_evict", idCached)
        assertEquals(1, searchCount)

        // Evict folder
        client.evictFolder("folder_to_evict")

        // Next resolution must re-query network
        val idAfterEvict = client.ensureFolderHierarchy(listOf("Workouts"))
        assertEquals("folder_to_evict", idAfterEvict)
        assertEquals(2, searchCount)
    }

    @Test
    fun testDownloadFileById_atomicStaging_cleansUpPartFileOnFailureAndDoesNotCorruptDestination() {
        val destFile = tempFolder.newFile("workout.fit")
        destFile.writeBytes("ORIGINAL_UNTOUCHED_CONTENT".toByteArray())

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { _ ->
                throw java.io.IOException("Simulated connection reset mid-download")
            }
            .build()

        val client = GoogleDriveClient(
            tokenProvider = { "token" },
            client = okHttpClient
        )

        val result = client.downloadFileById("corrupt_stream_file_id", destFile)
        assertFalse("Download should fail on IOException", result)
        assertEquals("ORIGINAL_UNTOUCHED_CONTENT", destFile.readText())

        val parentDir = destFile.parentFile ?: File(".")
        val leftoverParts = parentDir.listFiles { f -> f.name.contains(".part_") } ?: emptyArray()
        assertEquals("Temporary .part files must be cleaned up on failure", 0, leftoverParts.size)
    }

    @Test
    fun testDownloadFileById_atomicStaging_successRenamesDurablyToDestination() {
        val destFile = tempFolder.newFile("complete.fit")
        val expectedBytes = "HEALTHY_FIT_BINARY_DATA_12345".toByteArray()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(expectedBytes.toResponseBody("application/octet-stream".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val client = GoogleDriveClient(
            tokenProvider = { "token" },
            client = okHttpClient
        )

        val result = client.downloadFileById("healthy_file_id", destFile)
        assertTrue("Download should succeed", result)
        assertArrayEquals(expectedBytes, destFile.readBytes())

        val parentDir = destFile.parentFile ?: File(".")
        val leftoverParts = parentDir.listFiles { f -> f.name.contains(".part_") } ?: emptyArray()
        assertEquals("Temporary .part files must not remain after success", 0, leftoverParts.size)
    }

    @Test
    fun testDownloadFileById_purgesStalePartFilesOlderThan24Hours() {
        val parentDir = tempFolder.root
        val stalePartFile = File(parentDir, "activity.fit.part_12345_abc")
        stalePartFile.writeText("stale_temp_bytes")
        stalePartFile.setLastModified(System.currentTimeMillis() - 90_000_000L) // > 24 hours ago

        val freshPartFile = File(parentDir, "activity.fit.part_67890_def")
        freshPartFile.writeText("fresh_temp_bytes")
        freshPartFile.setLastModified(System.currentTimeMillis() - 60_000L) // 1 minute ago

        val destFile = File(parentDir, "download_target.fit")
        val expectedBytes = "NEW_DOWNLOAD_BYTES".toByteArray()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(expectedBytes.toResponseBody("application/octet-stream".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val client = GoogleDriveClient(
            tokenProvider = { "token" },
            client = okHttpClient
        )

        val result = client.downloadFileById("fresh_id", destFile)
        assertTrue(result)
        assertFalse("Stale part file older than 24h should be purged", stalePartFile.exists())
        assertTrue("Fresh part file younger than 24h should be preserved", freshPartFile.exists())
    }
}

