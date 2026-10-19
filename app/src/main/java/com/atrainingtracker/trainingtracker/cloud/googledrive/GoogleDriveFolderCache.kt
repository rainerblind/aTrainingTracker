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

/**
 * Thread-safe, account-scoped in-memory cache for resolved Google Drive folder IDs.
 *
 * Guarantees cross-worker concurrency deduplication (REQ-DAT-022) while enforcing account isolation
 * and atomic eviction of stale folder entries (REQ-DAT-030.2).
 */
internal class GoogleDriveFolderCache {

    private val lock = Any()
    private val cache = mutableMapOf<String, String>()

    private fun makeKey(accountKey: String, folderNames: List<String>): String {
        return "$accountKey:${folderNames.joinToString("/")}"
    }

    /**
     * Retrieves the cached folder ID for the given account key and folder hierarchy.
     */
    fun get(accountKey: String, folderNames: List<String>): String? = synchronized(lock) {
        cache[makeKey(accountKey, folderNames)]
    }

    /**
     * Stores a resolved folder ID under the given account key and folder hierarchy.
     */
    fun put(accountKey: String, folderNames: List<String>, folderId: String): Unit = synchronized(lock) {
        cache[makeKey(accountKey, folderNames)] = folderId
    }

    /**
     * Atomically evicts any cache entries referencing the given folder ID across all hierarchies.
     */
    fun evictFolder(folderId: String): Unit = synchronized(lock) {
        val keysToRemove = cache.filterValues { it == folderId }.keys
        for (key in keysToRemove) {
            cache.remove(key)
        }
    }

    /**
     * Atomically invalidates a specific folder hierarchy for the given account key.
     */
    fun invalidateHierarchy(accountKey: String, folderNames: List<String>): Unit = synchronized(lock) {
        cache.remove(makeKey(accountKey, folderNames))
    }

    /**
     * Clears all cached entries across all accounts.
     */
    fun clear(): Unit = synchronized(lock) {
        cache.clear()
    }
}
