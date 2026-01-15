/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 -2026 Rainer Blind <rainer.blind@gmail.com>
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

package com.atrainingtracker.trainingtracker.exporter

import android.content.Context
import com.atrainingtracker.R
import org.json.JSONException
import org.json.JSONObject
import java.io.File


/**
 * A data class holding all necessary information for an export operation.
 * Can be serialized to and from JSON to be easily passed to background workers.
 */
data class ExportInfo(
    val fileBaseName: String,
    val fileFormat: FileFormat,
    val exportType: ExportType
) {

    val shortPath: String
        get() = fileFormat.dirName + File.separator + fileName

    val fileName: String
        get() = fileBaseName + fileFormat.fileEnding

    override fun toString(): String {
        return "$exportType: $fileFormat: $fileBaseName"
    }

   /* some methods to help with notifications */

    // method to get the title for the notification
    // returns something like "exporting to filesystem"  // TODO: check
    fun getExportTitle(context: Context, exporter: BaseExporter): String {
        return context.getString(
            R.string.notification_title,
            context.getString(exporter.getAction().getIngId()),
            context.getString(exportType.getUiId())
        )
    }

    // method ot get a message for exporting
    // returns something like "exporting TCX file to filesystem  // TODO: check
    fun getExportMessage(context: Context, exporter: BaseExporter): String {
        val notification_format_id: Int = when (exportType) {
            ExportType.FILE -> R.string.notification_export_file
            ExportType.DROPBOX -> R.string.notification_export_dropbox
            ExportType.COMMUNITY -> R.string.notification_export_community
        }

        return context.getString(
            notification_format_id,
            context.getString(exporter.getAction().getIngId()),
            context.getString(fileFormat.getUiNameId()),
            fileBaseName
        )
    }

    // method to get a positive answer for the user
    // returns something like "successfully uploaded TCX file"  // TODO: check
    fun getPositiveAnswer(context: Context, exporter: BaseExporter): String {
        val notification_format_id: Int = when (exportType) {
            ExportType.FILE -> R.string.notification_finished_file
            ExportType.DROPBOX -> R.string.notification_finished_dropbox
            ExportType.COMMUNITY -> R.string.notification_finished_community
        }

        return context.getString(
            notification_format_id,
            context.getString(exporter.getAction().getPastId()),
            context.getString(fileFormat.getUiNameId()),
            fileBaseName
        )
    }
    /**********************************************************************************************/

    /**
     * Serializes the ExportInfo object to a JSON string.
     * @return A JSON representation of the object.
     * @throws JSONException if there is an error creating the JSON object.
     */
    @Throws(JSONException::class)
    fun toJson(): String {
        return JSONObject().apply {
            put(KEY_FILE_BASE_NAME, fileBaseName)
            put(KEY_FILE_FORMAT, fileFormat.name)
            put(KEY_EXPORT_TYPE, exportType.name)
        }.toString()
    }

    companion object {
        // Keys for JSON serialization/deserialization
        private const val KEY_FILE_BASE_NAME = "fileBaseName"
        private const val KEY_FILE_FORMAT = "fileFormat"
        private const val KEY_EXPORT_TYPE = "exportType"

        /**
         * Deserializes a JSON string into an ExportInfo object.
         * @param json The JSON string to parse.
         * @return A new ExportInfo object.
         * @throws JSONException if the JSON is invalid or missing keys.
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun fromJson(json: String): ExportInfo {
            val jsonObject = JSONObject(json)
            val fileBaseName = jsonObject.getString(KEY_FILE_BASE_NAME)
            // Use valueOf() to safely convert string back to enum
            val fileFormat = FileFormat.valueOf(jsonObject.getString(KEY_FILE_FORMAT))
            val exportType = ExportType.valueOf(jsonObject.getString(KEY_EXPORT_TYPE))

            return ExportInfo(fileBaseName, fileFormat, exportType)
        }
    }
}