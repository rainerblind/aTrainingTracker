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
    private val baseUrl: String = "https://www.googleapis.com"
) {

    companion object {
        private const val TAG = "GoogleDriveClient"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val JSON_MIME_TYPE = "application/json; charset=UTF-8"
    }

    // In-memory cache for resolved folder hierarchy paths (e.g. "aTrainingTracker/Workouts" -> folderId)
    private val folderIdCache = ConcurrentHashMap<String, String>()

    /**
     * Resolves the Google Drive folder hierarchy idempotently.
     * Reuses existing directories or creates them if missing.
     *
     * @param folderNames Sequential folder segments (e.g. ["aTrainingTracker", "Workouts"])
     * @return The Drive folder ID of the terminal segment, or null on failure.
     */
    fun ensureFolderHierarchy(folderNames: List<String>): String? {
        val cacheKey = folderNames.joinToString("/")
        folderIdCache[cacheKey]?.let { return it }

        var currentParentId = "root"
        for (folderName in folderNames) {
            val existingId = findFolderIdByName(folderName, currentParentId)
            currentParentId = existingId ?: createFolder(folderName, currentParentId) ?: return null
        }

        folderIdCache[cacheKey] = currentParentId
        return currentParentId
    }

    /**
     * Finds a folder by name under the specified parent.
     */
    fun findFolderIdByName(folderName: String, parentId: String): String? {
        val query = "name = '$folderName' and '$parentId' in parents and mimeType = '$FOLDER_MIME_TYPE' and trashed = false"
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = "$baseUrl/drive/v3/files?q=$encodedQuery&fields=files(id,name)&spaces=drive"

        val request = Request.Builder()
            .url(url)
            .apply { addAuthHeader(this) }
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "findFolderIdByName failed with code: ${response.code}")
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
            Log.e(TAG, "Error finding folder $folderName: ${e.message}", e)
        }
        return null
    }

    /**
     * Creates a folder with the given name under parentId.
     */
    fun createFolder(folderName: String, parentId: String): String? {
        val url = "$baseUrl/drive/v3/files"
        val payload = JSONObject().apply {
            put("name", folderName)
            put("mimeType", FOLDER_MIME_TYPE)
            put("parents", JSONArray().put(parentId))
        }

        val requestBody = payload.toString().toRequestBody(JSON_MIME_TYPE.toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .apply { addAuthHeader(this) }
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "createFolder failed with code: ${response.code}")
                    return null
                }
                val bodyString = response.body?.string() ?: return null
                val json = JSONObject(bodyString)
                return json.optString("id").takeIf { it.isNotBlank() }
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

        val request = Request.Builder()
            .url(url)
            .apply { addAuthHeader(this) }
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
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

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addPart(metadataJson.toString().toRequestBody(JSON_MIME_TYPE.toMediaTypeOrNull()))
            .addPart(file.asRequestBody(mimeType.toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url(url)
            .apply { addAuthHeader(this) }
            .post(multipartBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
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
        val request = Request.Builder()
            .url(url)
            .apply { addAuthHeader(this) }
            .patch(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
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
     * Downloads a file from Google Drive directly into the destination file.
     */
    fun downloadFile(folderId: String, fileName: String, destinationFile: File): Boolean {
        val fileId = findFileIdByName(folderId, fileName)
        if (fileId == null) {
            Log.e(TAG, "Cannot download: file $fileName not found in folder $folderId")
            return false
        }

        val url = "$baseUrl/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .apply { addAuthHeader(this) }
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "downloadFile failed with code: ${response.code}")
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
            Log.e(TAG, "Error downloading file $fileName: ${e.message}", e)
            return false
        }
    }

    private fun addAuthHeader(builder: Request.Builder) {
        val token = tokenProvider()
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
    }
}
