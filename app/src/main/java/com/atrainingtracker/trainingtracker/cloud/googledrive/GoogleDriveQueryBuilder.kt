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
 * Structured query builder and parameter escaping utility for Google Drive API v3 queries.
 *
 * Enforces strict escaping of backslashes and single quotes per Drive API v3 search clause specification
 * (REQ-DAT-030.1), preventing HTTP 400 Bad Request parse errors on names containing special characters.
 */
internal object GoogleDriveQueryBuilder {

    private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

    /**
     * Escapes single quotes and backslashes in query parameter string literals.
     * Backslashes must be escaped before single quotes to prevent double-escaping.
     */
    fun escapeQueryValue(value: String): String {
        return value.replace("\\", "\\\\").replace("'", "\\'")
    }

    /**
     * Builds a folder search query by folder name and parent ID.
     */
    fun buildFolderQuery(folderName: String, parentId: String): String {
        val escapedName = escapeQueryValue(folderName)
        val escapedParent = escapeQueryValue(parentId)
        return "name = '$escapedName' and '$escapedParent' in parents and mimeType = '$FOLDER_MIME_TYPE' and trashed = false"
    }

    /**
     * Builds a file search query by file name and parent folder ID.
     */
    fun buildFileQuery(fileName: String, folderId: String): String {
        val escapedName = escapeQueryValue(fileName)
        val escapedFolderId = escapeQueryValue(folderId)
        return "name = '$escapedName' and '$escapedFolderId' in parents and trashed = false"
    }

    /**
     * Builds a children query for recursive directory traversal under the specified folder ID.
     */
    fun buildChildrenQuery(folderId: String): String {
        val escapedFolderId = escapeQueryValue(folderId)
        return "'$escapedFolderId' in parents and trashed = false"
    }
}
