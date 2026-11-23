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

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.util.Log
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveAuthManager
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveClient
import com.atrainingtracker.trainingtracker.migration.parsers.WorkoutParser
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.RateLimitException
import com.dropbox.core.v2.DbxClientV2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Shared cloud workout discovery, rate-limited download retry, remote filename sanitization,
 * and [SecureZipExtractor]-hardened `.zip` staging pipeline consumed by both [AutoImportManager]
 * and [LegacyImportEngine] (`REQ-MIG-061.3`, `REQ-MIG-056`, `REQ-MIG-040`).
 */
object CloudWorkoutSourceScanner {

    private const val TAG = "CloudWorkoutScanner"

    data class CloudRemoteEntry(
        val id: String,
        val name: String,
        val pathLower: String? = null
    )

    /**
     * Normalizes a workout filename into its deduplication base name by stripping the file
     * extension and trailing temporary suffixes (`-TMP`, `~`).
     */
    fun normalizeBaseFileName(fileName: String): String {
        return fileName.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~")
    }

    /**
     * Returns target workout extensions for the requested [format] (`"fit"`, `"tcx"`, `"gpx"`, or `"all"`).
     */
    fun targetExtensionsFor(format: String, includeZip: Boolean = false): List<String> {
        val base = when (format.lowercase(Locale.ROOT)) {
            "tcx" -> listOf(".tcx")
            "gpx" -> listOf(".gpx")
            "fit" -> listOf(".fit")
            else -> listOf(".fit", ".tcx", ".gpx")
        }
        return if (includeZip) base + ".zip" else base
    }

    /**
     * Returns candidate Dropbox folder paths for the requested [format].
     */
    fun dropboxPathsFor(format: String, includeWorkoutsRoot: Boolean = false): List<String> {
        val fitPaths = if (includeWorkoutsRoot) {
            listOf("/FIT", "/apps/Workouts/FIT", "/Workouts/FIT", "/Workouts")
        } else {
            listOf("/FIT", "/apps/Workouts/FIT", "/Workouts/FIT")
        }
        return when (format.lowercase(Locale.ROOT)) {
            "tcx" -> listOf("/TCX", "/apps/Workouts/TCX")
            "gpx" -> listOf("/GPX", "/apps/Workouts/GPX")
            "fit" -> fitPaths
            else -> listOf("/TCX", "/apps/Workouts/TCX", "/GPX", "/apps/Workouts/GPX") + fitPaths
        }
    }

    /**
     * Returns candidate Google Drive folder hierarchies for the requested [format].
     */
    fun googleDriveFolderHierarchiesFor(format: String, includeAppRoot: Boolean = true): List<List<String>> {
        val appRoot = if (includeAppRoot) listOf(listOf("aTrainingTracker")) else emptyList()
        return when (format.lowercase(Locale.ROOT)) {
            "fit" -> listOf(
                listOf("FIT"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "FIT"),
                listOf("aTrainingTracker", "Workouts")
            ) + appRoot
            "tcx" -> listOf(
                listOf("TCX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "TCX"),
                listOf("aTrainingTracker", "Workouts")
            ) + appRoot
            "gpx" -> listOf(
                listOf("GPX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "GPX"),
                listOf("aTrainingTracker", "Workouts")
            ) + appRoot
            else -> listOf(
                listOf("FIT"),
                listOf("TCX"),
                listOf("GPX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "FIT"),
                listOf("aTrainingTracker", "TCX"),
                listOf("aTrainingTracker", "GPX"),
                listOf("aTrainingTracker", "Workouts")
            ) + appRoot
        }
    }

    /**
     * Deduplicates remote entries by normalized base filename and sorts ascending case-insensitively
     * (`REQ-MIG-031`, `REQ-MIG-049.4`).
     */
    fun deduplicateAndSort(entries: List<CloudRemoteEntry>): List<CloudRemoteEntry> {
        return entries.distinctBy {
            normalizeBaseFileName(it.name).lowercase(Locale.ROOT)
        }.sortedWith { a, b ->
            String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name)
        }
    }

    /**
     * Creates a configured [DbxClientV2] from the persisted Dropbox credential, or `null` if unavailable.
     */
    fun createDropboxClient(): DbxClientV2? {
        val credential = TrainingApplication.readDropboxCredential() ?: return null
        return DbxClientV2(DbxRequestConfig(BuildConfig.DROPBOX_APP_KEY), credential)
    }

    /**
     * Ensures a valid Google Drive OAuth token (refreshing synchronously if blank) and creates a
     * [GoogleDriveClient], or returns `null` if unavailable.
     */
    fun createGoogleDriveClient(context: Context): GoogleDriveClient? {
        val appContext = context.applicationContext ?: context
        var token = TrainingApplication.getGoogleDriveAuthToken()
        if (token.isNullOrBlank()) {
            token = GoogleDriveAuthManager.refreshTokenSync(appContext).getOrNull()
        }
        if (token.isNullOrBlank()) {
            return null
        }
        return GoogleDriveClient(
            tokenProvider = { TrainingApplication.getGoogleDriveAuthToken() },
            tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(appContext).getOrNull() },
            accountProvider = { TrainingApplication.getGoogleDriveAccountEmail() }
        )
    }

    /**
     * Lists Dropbox entries recursively across candidate paths for [format], continuing pagination
     * safely and returning deduplicated, ascending-sorted entries.
     */
    fun listDropboxEntries(
        dbxClient: DbxClientV2,
        format: String,
        includeWorkoutsRoot: Boolean = false,
        includeZip: Boolean = false,
        onStatus: ((String) -> Unit)? = null
    ): List<CloudRemoteEntry> {
        val possiblePaths = dropboxPathsFor(format, includeWorkoutsRoot)
        val targetExtensions = targetExtensionsFor(format, includeZip)
        val allEntries = mutableListOf<CloudRemoteEntry>()

        for (path in possiblePaths) {
            try {
                onStatus?.invoke("Scanning $path...")
                var result = dbxClient.files().listFolderBuilder(path).withRecursive(true).start()
                while (true) {
                    result.entries
                        .filter { entry -> targetExtensions.any { entry.name.lowercase(Locale.ROOT).endsWith(it) } }
                        .forEach { entry ->
                            allEntries.add(
                                CloudRemoteEntry(
                                    id = entry.pathLower ?: entry.name,
                                    name = entry.name,
                                    pathLower = entry.pathLower
                                )
                            )
                        }
                    if (!result.hasMore) break
                    try {
                        result = dbxClient.files().listFolderContinue(result.cursor)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error continuing Dropbox folder listing for $path, keeping ${allEntries.size} entries", e)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Dropbox folder scan failed for $path: ${e.message}")
            }
        }

        return deduplicateAndSort(allEntries)
    }

    /**
     * Lists Google Drive entries recursively across candidate folder hierarchies for [format],
     * skipping duplicate resolved folder IDs and returning deduplicated, ascending-sorted entries.
     */
    fun listGoogleDriveEntries(
        driveClient: GoogleDriveClient,
        format: String,
        includeAppRoot: Boolean = true,
        includeZip: Boolean = false,
        onStatus: ((String) -> Unit)? = null
    ): List<CloudRemoteEntry> {
        val targetExtensions = targetExtensionsFor(format, includeZip)
        val candidateHierarchies = googleDriveFolderHierarchiesFor(format, includeAppRoot)
        val allEntries = mutableListOf<CloudRemoteEntry>()
        val searchedFolderIds = mutableSetOf<String>()

        for (hierarchy in candidateHierarchies) {
            try {
                val folderDisplay = hierarchy.joinToString("/")
                onStatus?.invoke("Scanning Google Drive: $folderDisplay...")
                val folderId = driveClient.resolveFolderHierarchy(hierarchy)
                if (folderId != null && searchedFolderIds.add(folderId)) {
                    val scanResult = driveClient.listFilesRecursivelyResult(folderId, targetExtensions)
                    scanResult.entries.forEach { file ->
                        allEntries.add(CloudRemoteEntry(id = file.id, name = file.name))
                    }
                    if (!scanResult.isComplete) {
                        Log.w(TAG, "Google Drive folder scan incomplete for $folderDisplay: ${scanResult.error?.message}")
                        onStatus?.invoke("Google Drive scan incomplete: ${scanResult.error?.message ?: "unknown error"}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Google Drive folder scan failed for ${hierarchy.joinToString("/")}: ${e.message}")
            }
        }

        return deduplicateAndSort(allEntries)
    }

    /**
     * Downloads a Dropbox file with [RateLimitException] backoff (`backoffMillis + 500ms`)
     * and transient retry (`REQ-MIG-061.3`).
     */
    suspend fun downloadDropboxWithRetry(
        dbxClient: DbxClientV2,
        pathLower: String,
        targetFile: File,
        maxRetries: Int = 3,
        delayFn: suspend (Long) -> Unit = ::delay
    ): Boolean {
        var attempt = 0
        while (attempt < maxRetries) {
            attempt++
            try {
                FileOutputStream(targetFile).use { fos ->
                    dbxClient.files().download(pathLower).download(fos)
                }
                return true
            } catch (e: RateLimitException) {
                val backoffMs = e.backoffMillis + 500L
                Log.w(TAG, "Dropbox rate limit hit for $pathLower. Backing off for ${backoffMs}ms (attempt $attempt/$maxRetries)...")
                if (attempt >= maxRetries) return false
                delayFn(backoffMs)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Transient Dropbox download error for $pathLower (attempt $attempt/$maxRetries): ${e.message}")
                if (attempt >= maxRetries) return false
                delayFn(1000L * attempt)
            }
        }
        return false
    }

    /**
     * Downloads a Google Drive file with transient retry backoff (`REQ-MIG-061.3`).
     */
    suspend fun downloadGoogleDriveWithRetry(
        driveClient: GoogleDriveClient,
        fileId: String,
        targetFile: File,
        maxRetries: Int = 3,
        delayFn: suspend (Long) -> Unit = ::delay
    ): Boolean {
        var attempt = 0
        while (attempt < maxRetries) {
            attempt++
            try {
                val success = driveClient.downloadFileById(fileId, targetFile)
                if (success) return true
                if (attempt >= maxRetries) return false
                delayFn(1000L * attempt)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Transient Google Drive download error for $fileId (attempt $attempt/$maxRetries): ${e.message}")
                if (attempt >= maxRetries) return false
                delayFn(1000L * attempt)
            }
        }
        return false
    }

    /**
     * Validates a remote filename strictly inside [workerDir] via [SecureZipExtractor.sanitizeDriveFileName].
     * Deletes [workerDir] and returns `null` when any traversal or invalid filename violation is detected.
     */
    fun sanitizeRemoteTargetFile(entryName: String, workerDir: File): File? {
        return try {
            SecureZipExtractor.sanitizeDriveFileName(entryName, workerDir)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security violation: Invalid remote file name: $entryName", e)
            workerDir.deleteRecursively()
            null
        }
    }

    /**
     * Prepares downloaded file(s) for workout ingestion. If [downloadedFile] is a `.zip` archive,
     * unpacks it into an isolated child directory under [stagingRoot] strictly via
     * [SecureZipExtractor.extractWorkoutArchive], throwing [SecurityException] on any Zip-Slip,
     * symlink, null-byte, or zip-bomb violation, and returns only supported workout files
     * (`.fit`, `.tcx`, `.gpx`).
     */
    fun prepareLocalImportFiles(downloadedFile: File, stagingRoot: File): List<File> {
        if (!downloadedFile.name.endsWith(".zip", ignoreCase = true)) {
            return listOf(downloadedFile)
        }
        val extractDir = File(stagingRoot, "zip_extract")
        val extracted = SecureZipExtractor.extractWorkoutArchive(downloadedFile, extractDir)
        return extracted.filter { WorkoutParser.forFormat(it.name) != null }
    }
}
