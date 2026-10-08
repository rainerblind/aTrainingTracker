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
}
