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
 */

package com.atrainingtracker.trainingtracker.cloud.googledrive

import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Metadata entry for a Google Drive file returned by recursive directory scans.
 */
data class DriveFileEntry(
    val id: String,
    val name: String,
    val size: Long? = null,
    val mimeType: String? = null
)

/**
 * Lightweight, robust REST client for Google Drive API v3.
 *
 * Communicates directly via HTTPS using OkHttp, strictly restricted to the least-privilege
 * scope `https://www.googleapis.com/auth/drive.file`.
 */
class GoogleDriveClient(
    private val tokenProvider: () -> String?,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://www.googleapis.com",
    private val tokenRefresher: (() -> String?)? = null
) {

    companion object {
        private const val TAG = "GoogleDriveClient"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val JSON_MIME_TYPE = "application/json; charset=UTF-8"

        private val hierarchyLock = Any()
        private val globalFolderIdCache = ConcurrentHashMap<String, String>()

        @androidx.annotation.VisibleForTesting
        fun clearFolderCache() {
            globalFolderIdCache.clear()
        }
    }

    /** HTTP status code of the most recent network call, or null if no call has been made. */
    @Volatile
    var lastHttpCode: Int? = null
        private set

    /** Error body snippet extracted from the most recent non-successful response, or null. */
    @Volatile
    var lastErrorMessage: String? = null
        private set

    @Volatile
    private var activeToken: String? = null

    private fun getAuthToken(): String? {
        return activeToken ?: tokenProvider().also { activeToken = it }
    }

    private fun executeCallWithRetry(buildRequest: (token: String?) -> Request): Response {
        var token = getAuthToken()
        var request = buildRequest(token)
        var response = client.newCall(request).execute()
        lastHttpCode = response.code

        if (response.code == 401 && tokenRefresher != null) {
            Log.w(TAG, "Encountered HTTP 401 Unauthorized; invoking tokenRefresher...")
            val refreshedToken = tokenRefresher.invoke()
            if (!refreshedToken.isNullOrBlank()) {
                activeToken = refreshedToken
                response.close()
                request = buildRequest(refreshedToken)
                response = client.newCall(request).execute()
                lastHttpCode = response.code
            }
        }

        if (!response.isSuccessful) {
            lastErrorMessage = try {
                response.peekBody(2048L).string()
            } catch (e: Exception) {
                null
            }
        } else {
            lastErrorMessage = null
        }

        return response
    }

    /**
     * Resolves the Google Drive folder hierarchy idempotently.
     * Reuses existing directories or creates them if missing.
     *
     * @param folderNames Sequential folder segments (e.g. ["aTrainingTracker", "Workouts"])
     * @return The Drive folder ID of the terminal segment, or null on failure.
     */
    fun ensureFolderHierarchy(folderNames: List<String>): String? {
        val cacheKey = folderNames.joinToString("/")
        globalFolderIdCache[cacheKey]?.let { return it }

        synchronized(hierarchyLock) {
            globalFolderIdCache[cacheKey]?.let { return it }

            var currentParentId = "root"
            for (folderName in folderNames) {
                val existingId = findFolderIdByName(folderName, currentParentId)
                currentParentId = existingId ?: createFolder(folderName, currentParentId) ?: return null
            }

            globalFolderIdCache[cacheKey] = currentParentId
            return currentParentId
        }
    }

    /**
     * Finds a folder by name under the specified parent.
     */
    fun findFolderIdByName(folderName: String, parentId: String): String? {
        val query = "name = '$folderName' and '$parentId' in parents and mimeType = '$FOLDER_MIME_TYPE' and trashed = false"
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = "$baseUrl/drive/v3/files?q=$encodedQuery&fields=files(id,name)&spaces=drive"

        try {
            executeCallWithRetry { token ->
                Request.Builder()
                    .url(url)
                    .apply { addAuthHeader(this, token) }
                    .get()
                    .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "findFolderIdByName failed with code: ${response.code}")
                    return null
                }
                val bodyString = response.body?.string() ?: return null
                val json = JSONObject(bodyString)
                val files = json.optJSONArray("files") ?: return null
                if (files.length() > 0) {
                    if (files.length() > 1) {
                        Log.w(TAG, "Multiple folders found with name '$folderName' under '$parentId' (count: ${files.length()}). Reusing first folder ID: ${files.getJSONObject(0).getString("id")}")
                    }
                    return files.getJSONObject(0).getString("id")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding folder $folderName: ${e.message}", e)
        }
        return null
    }

    /**
     * Creates a folder with the given name under parentId.
     * Omits parents when parentId == "root" or blank for canonical Drive v3 compatibility.
     */
    fun createFolder(folderName: String, parentId: String): String? {
        val url = "$baseUrl/drive/v3/files"
        val payload = JSONObject().apply {
            put("name", folderName)
            put("mimeType", FOLDER_MIME_TYPE)
            if (parentId.isNotBlank() && parentId != "root") {
                put("parents", JSONArray().put(parentId))
            }
        }

        val requestBody = payload.toString().toRequestBody(JSON_MIME_TYPE.toMediaTypeOrNull())

        try {
            executeCallWithRetry { token ->
                Request.Builder()
                    .url(url)
                    .apply { addAuthHeader(this, token) }
                    .post(requestBody)
                    .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "createFolder failed with code: ${response.code}")
                    return null
                }
                val bodyString = response.body?.string() ?: return null
                val json = JSONObject(bodyString)
                val newFolderId = json.optString("id").takeIf { it.isNotBlank() }
                if (newFolderId != null) {
                    Log.i(TAG, "Created folder '$folderName' under parent '$parentId' (id: $newFolderId)")
                }
                return newFolderId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating folder $folderName: ${e.message}", e)
        }
        return null
    }

    /**
     * Searches for a file by name within a folder.
     */
    fun findFileIdByName(folderId: String, fileName: String): String? {
        val query = "name = '$fileName' and '$folderId' in parents and trashed = false"
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = "$baseUrl/drive/v3/files?q=$encodedQuery&fields=files(id,name)&spaces=drive"

        try {
            executeCallWithRetry { token ->
                Request.Builder()
                    .url(url)
                    .apply { addAuthHeader(this, token) }
                    .get()
                    .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "findFileIdByName failed with code: ${response.code}")
                    return null
                }
                val bodyString = response.body?.string() ?: return null
                val json = JSONObject(bodyString)
                val files = json.optJSONArray("files") ?: return null
                if (files.length() > 0) {
                    return files.getJSONObject(0).getString("id")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching file $fileName: ${e.message}", e)
        }
        return null
    }

    /**
     * Uploads a file to the specified folder, overwriting if a file with the same name already exists.
     */
    fun uploadOrOverwriteFile(folderId: String, fileName: String, mimeType: String, file: File): Boolean {
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: ${file.absolutePath}")
            return false
        }

        val existingFileId = findFileIdByName(folderId, fileName)
        return if (existingFileId != null) {
            updateExistingFile(existingFileId, mimeType, file)
        } else {
            createMultipartFile(folderId, fileName, mimeType, file)
        }
    }

    private fun createMultipartFile(folderId: String, fileName: String, mimeType: String, file: File): Boolean {
        val url = "$baseUrl/upload/drive/v3/files?uploadType=multipart"

        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray().put(folderId))
        }

        try {
            executeCallWithRetry { token ->
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addPart(metadataJson.toString().toRequestBody(JSON_MIME_TYPE.toMediaTypeOrNull()))
                    .addPart(file.asRequestBody(mimeType.toMediaTypeOrNull()))
                    .build()

                Request.Builder()
                    .url(url)
                    .apply { addAuthHeader(this, token) }
                    .post(multipartBody)
                    .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "createMultipartFile failed with code: ${response.code}")
                    return false
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file $fileName: ${e.message}", e)
            return false
        }
    }

    private fun updateExistingFile(fileId: String, mimeType: String, file: File): Boolean {
        val url = "$baseUrl/upload/drive/v3/files/$fileId?uploadType=media"

        val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())

        try {
            executeCallWithRetry { token ->
                Request.Builder()
                    .url(url)
                    .apply { addAuthHeader(this, token) }
                    .patch(requestBody)
                    .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "updateExistingFile failed with code: ${response.code}")
                    return false
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating file $fileId: ${e.message}", e)
            return false
        }
    }

    /**
     * Resolves the Google Drive folder hierarchy without creating missing directories.
     *
     * @param folderNames Sequential folder segments (e.g. ["aTrainingTracker", "Workouts"])
     * @return The Drive folder ID of the terminal segment, or null if any segment does not exist.
     */
    fun resolveFolderHierarchy(folderNames: List<String>): String? {
        val cacheKey = folderNames.joinToString("/")
        globalFolderIdCache[cacheKey]?.let { return it }

        var currentParentId = "root"
        for (folderName in folderNames) {
            val existingId = findFolderIdByName(folderName, currentParentId) ?: return null
            currentParentId = existingId
        }

        globalFolderIdCache[cacheKey] = currentParentId
        return currentParentId
    }

    /**
     * Traverses the specified folder hierarchy recursively and returns all files matching
     * the requested file extensions.
     *
     * @param folderId The root folder ID to start the scan from.
     * @param extensions Optional list of file extensions to filter on (e.g. [".fit", ".tcx", ".gpx"]).
     *                   If empty, all non-folder files are returned.
     * @return List of matching [DriveFileEntry] objects.
     */
    fun listFilesRecursively(
        folderId: String,
        extensions: List<String> = listOf(".fit", ".tcx", ".gpx")
    ): List<DriveFileEntry> {
        val results = mutableListOf<DriveFileEntry>()
        val folderQueue = ArrayDeque<String>()
        val visitedFolderIds = mutableSetOf<String>()
        folderQueue.add(folderId)
        visitedFolderIds.add(folderId)

        val lowerExts = extensions.map { it.lowercase() }

        while (folderQueue.isNotEmpty()) {
            val currentFolderId = folderQueue.removeFirst()
            var pageToken: String? = null

            do {
                val query = "'$currentFolderId' in parents and trashed = false"
                val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
                var url = "$baseUrl/drive/v3/files?q=$encodedQuery&fields=nextPageToken,files(id,name,size,mimeType)&pageSize=1000&spaces=drive"
                if (!pageToken.isNullOrBlank()) {
                    val encodedToken = URLEncoder.encode(pageToken, StandardCharsets.UTF_8.name())
                    url += "&pageToken=$encodedToken"
                }

                try {
                    executeCallWithRetry { token ->
                        Request.Builder()
                            .url(url)
                            .apply { addAuthHeader(this, token) }
                            .get()
                            .build()
                    }.use { response ->
                        if (!response.isSuccessful) {
                            Log.e(TAG, "listFilesRecursively failed for folder $currentFolderId with code: ${response.code}")
                            pageToken = null
                            return@use
                        }
                        val bodyString = response.body?.string() ?: run {
                            pageToken = null
                            return@use
                        }
                        val json = JSONObject(bodyString)
                        pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
                        val filesArray = json.optJSONArray("files")
                        if (filesArray != null) {
                            for (i in 0 until filesArray.length()) {
                                val fileObj = filesArray.getJSONObject(i)
                                val id = fileObj.getString("id")
                                val name = fileObj.getString("name")
                                val mimeType = fileObj.optString("mimeType")
                                val size = if (fileObj.has("size")) fileObj.optLong("size") else null

                                if (mimeType == FOLDER_MIME_TYPE) {
                                    if (visitedFolderIds.add(id)) {
                                        folderQueue.add(id)
                                    }
                                } else {
                                    val nameLower = name.lowercase()
                                    if (lowerExts.isEmpty() || lowerExts.any { nameLower.endsWith(it) }) {
                                        results.add(DriveFileEntry(id, name, size, mimeType))
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error listing files in folder $currentFolderId: ${e.message}", e)
                    pageToken = null
                }
            } while (pageToken != null)
        }

        return results
    }

    /**
     * Downloads a file by its Google Drive file ID directly into the destination file.
     */
    fun downloadFileById(fileId: String, destinationFile: File): Boolean {
        val url = "$baseUrl/drive/v3/files/$fileId?alt=media"

        try {
            executeCallWithRetry { token ->
                Request.Builder()
                    .url(url)
                    .apply { addAuthHeader(this, token) }
                    .get()
                    .build()
            }.use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "downloadFileById failed for file $fileId with code: ${response.code}")
                    return false
                }
                val body = response.body ?: return false
                FileOutputStream(destinationFile).use { out ->
                    body.byteStream().use { input ->
                        input.copyTo(out)
                    }
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file $fileId: ${e.message}", e)
            return false
        }
    }

    /**
     * Downloads a file from Google Drive directly into the destination file.
     */
    fun downloadFile(folderId: String, fileName: String, destinationFile: File): Boolean {
        val fileId = findFileIdByName(folderId, fileName)
        if (fileId == null) {
            Log.e(TAG, "Cannot download: file $fileName not found in folder $folderId")
            return false
        }
        return downloadFileById(fileId, destinationFile)
    }

    private fun addAuthHeader(builder: Request.Builder, token: String? = getAuthToken()) {
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
    }
}
