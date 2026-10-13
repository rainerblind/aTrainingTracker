/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.cloud.googledrive

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests verifying recursive folder traversal, pagination, extension filtering,
 * and direct file ID downloading in [GoogleDriveClient] (REQ-MIG-034, TST-MIG-031).
 */
class GoogleDriveClientRecursiveListingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testListFilesRecursively_filtersByTargetExtensionsAndTraversesSubfolders() {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()

                val jsonResponse = when {
                    url.contains("root_folder_id") -> {
                        """
                        {
                            "files": [
                                {"id": "sub_folder_1", "name": "WorkoutsSub", "mimeType": "application/vnd.google-apps.folder"},
                                {"id": "file_1", "name": "run_2026_01.fit", "size": 1024, "mimeType": "application/octet-stream"},
                                {"id": "file_2", "name": "notes.txt", "size": 512, "mimeType": "text/plain"}
                            ]
                        }
                        """.trimIndent()
                    }
                    url.contains("sub_folder_1") -> {
                        """
                        {
                            "files": [
                                {"id": "file_3", "name": "ride_2026_02.tcx", "size": 2048, "mimeType": "application/vnd.garmin.tcx+xml"},
                                {"id": "file_4", "name": "hike_2026_03.gpx", "size": 4096, "mimeType": "application/gpx+xml"},
                                {"id": "file_5", "name": "photo.jpg", "size": 8192, "mimeType": "image/jpeg"}
                            ]
                        }
                        """.trimIndent()
                    }
                    else -> """{"files": []}"""
                }

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonResponse.toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val client = GoogleDriveClient(
            tokenProvider = { "test_token" },
            client = okHttpClient
        )

        val files = client.listFilesRecursively(
            folderId = "root_folder_id",
            extensions = listOf(".fit", ".tcx", ".gpx")
        )

        assertEquals("Should discover exactly 3 workout files across root and subfolder", 3, files.size)
        val fileNames = files.map { it.name }
        assertTrue("Should contain run_2026_01.fit", fileNames.contains("run_2026_01.fit"))
        assertTrue("Should contain ride_2026_02.tcx", fileNames.contains("ride_2026_02.tcx"))
        assertTrue("Should contain hike_2026_03.gpx", fileNames.contains("hike_2026_03.gpx"))
        assertFalse("Should exclude notes.txt", fileNames.contains("notes.txt"))
        assertFalse("Should exclude photo.jpg", fileNames.contains("photo.jpg"))
    }

    @Test
    fun testListFilesRecursively_handlesPaginationWithNextPageToken() {
        var pageCount = 0
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                pageCount++

                val jsonResponse = if (!url.contains("pageToken=")) {
                    // Page 1
                    """
                    {
                        "nextPageToken": "page_token_2",
                        "files": [
                            {"id": "fit_1", "name": "activity_1.fit", "size": 1000, "mimeType": "application/octet-stream"}
                        ]
                    }
                    """.trimIndent()
                } else {
                    // Page 2 (last page)
                    """
                    {
                        "files": [
                            {"id": "fit_2", "name": "activity_2.fit", "size": 2000, "mimeType": "application/octet-stream"}
                        ]
                    }
                    """.trimIndent()
                }

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonResponse.toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val client = GoogleDriveClient(
            tokenProvider = { "test_token" },
            client = okHttpClient
        )

        val files = client.listFilesRecursively("folder_page_test", listOf(".fit"))
        assertEquals("Both pages must be retrieved", 2, files.size)
        assertEquals("Must execute 2 page requests", 2, pageCount)
        assertEquals("activity_1.fit", files[0].name)
        assertEquals("activity_2.fit", files[1].name)
    }

    @Test
    fun testDownloadFileById_writesBinaryContentToTargetFile() {
        val expectedBytes = "SIMULATED_FIT_FILE_BINARY_CONTENT".toByteArray()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()

                if (url.contains("files/test_download_id?alt=media")) {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(expectedBytes.toResponseBody("application/octet-stream".toMediaTypeOrNull()))
                        .build()
                } else {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody(null))
                        .build()
                }
            }
            .build()

        val client = GoogleDriveClient(
            tokenProvider = { "valid_token" },
            client = okHttpClient
        )

        val destination = tempFolder.newFile("downloaded.fit")
        val success = client.downloadFileById("test_download_id", destination)

        assertTrue("Download should succeed", success)
        assertArrayEquals("Downloaded bytes must match source payload", expectedBytes, destination.readBytes())
    }

    @Test
    fun testResolveFolderHierarchy_returnsNullWhenFolderDoesNotExist() {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                // Return empty files list -> folder not found
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
            tokenProvider = { "valid_token" },
            client = okHttpClient
        )

        val resolved = client.resolveFolderHierarchy(listOf("NonExistentFolder", "SubFolder"))
        assertNull("Non-existent folder hierarchy should resolve to null", resolved)
    }
}
