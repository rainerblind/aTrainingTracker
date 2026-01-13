/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2025
 * Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.exporter

import android.content.Context
import android.database.Cursor
import android.util.Log
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

class StravaUploader(context: Context) : BaseExporter(context) {

    companion object {
        private const val TAG = "StravaUploader"
        private val DEBUG = TrainingApplication.getDebug(false)

        private const val URL_STRAVA_UPLOAD = "https://www.strava.com/api/v3/uploads"
        private const val URL_STRAVA_ACTIVITY = "https://www.strava.com/api/v3/activities/"

        private const val MAX_REQUESTS = 10
        private const val INITIAL_WAITING_TIME = 1000L // 1 seconds
        private const val INCREASE_WAITING_TIME_MULT = 1.4

        // JSON / Form Keys
        private const val ID = "id"
        private const val ACTIVITY_ID = "activity_id"
        private const val ERROR = "error"
        private const val STATUS = "status"
        private const val DATA_TYPE = "data_type"
        private const val TCX = "tcx"
        private const val FILE = "file"

        // Update fields
        private const val NAME = "name"
        private const val TYPE = "type"
        private const val GEAR_ID = "gear_id"
        private const val DESCRIPTION = "description"
        private const val COMMUTE = "commute"
        private const val TRAINER = "trainer"

        // Strava Status messages
        private const val STATUS_PROCESSING = "Your activity is still being processed."
        private const val STATUS_DELETED = "The created activity has been deleted."
        private const val STATUS_ERROR = "There was an error processing your activity."
        private const val STATUS_READY = "Your activity is ready."
    }

    private val client = OkHttpClient()

    override fun getAction(): Action {
        return Action.UPLOAD
    }


    override fun doExport(exportInfo: ExportInfo): ExportResult {
        if (DEBUG) Log.d(TAG, "doExport: ${exportInfo.fileBaseName}")

        val file = File(getBaseDirFile(mContext), exportInfo.shortPath)
        val accessToken = StravaHelper.getRefreshedAccessToken()

        if (DEBUG) Log.d(TAG, "starting to upload to strava")

        // 1. Build Multipart Request
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(DATA_TYPE, TCX)
            .addFormDataPart(
                FILE,
                file.name,
                file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(URL_STRAVA_UPLOAD)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(requestBody)
            .build()

        // 2. Execute Request
        val (responseBody, responseCode) = try {
            client.newCall(request).execute().use { response ->
                Pair(response.body?.string() ?: "", response.code)
            }
        } catch (e: IOException) {
            return ExportResult(false, true, "Network error: ${e.message}")  // a network error -> retry
        }

        if (DEBUG) Log.d(TAG, "uploadToStrava response: $responseBody")

        if (responseBody.isEmpty()) {
            return ExportResult(false, false, "no response from Strava")  // probably something strange -> do not retry
        }

        // 3. Handle Errors
        if (responseCode != 201 && responseCode != 200) {
            if (DEBUG) Log.d(TAG, "bad response code: $responseCode")

            return ExportResult(false, false, "API Error: $responseBody")  // probably something strange -> do not retry
        }

        // 4. Handle Success (Initial Upload)
        if (DEBUG) Log.d(TAG, "Successfully uploaded to STRAVA, checking result")
        notifyExportFinished(mContext.getString(R.string.strava_success_but_must_check))

        val uploadResponseJson = JSONObject(responseBody)

        if (uploadResponseJson.has(ERROR) && !uploadResponseJson.isNull(ERROR) && uploadResponseJson.getString(ERROR) != "null") {
            return ExportResult(false, false, uploadResponseJson.getString(ERROR))  // probably something strange -> do not retry
        } else if (uploadResponseJson.has(ID)) {
            val uploadId = uploadResponseJson.getString(ID)
            val stravaUploadDbHelper = StravaUploadDbHelper(mContext)

            stravaUploadDbHelper.updateUploadId(exportInfo.fileBaseName, uploadId)
            stravaUploadDbHelper.updateStatus(exportInfo.fileBaseName, "Uploaded to STRAVA, checking result")
            notifyExportFinished(mContext.getString(R.string.strava_success_but_must_check))

            // 5. Poll for processing status
            var exportResult: ExportResult? = null
            var waitingTime = INITIAL_WAITING_TIME

            for (attempt in 1..MAX_REQUESTS) {
                if (exportResult != null) break
                Thread.sleep(waitingTime)
                waitingTime = (waitingTime * INCREASE_WAITING_TIME_MULT).toLong()

                val uploadStatusJsonAnswer = getStravaUploadStatus(uploadId)
                if (uploadStatusJsonAnswer == null) {
                    exportResult = ExportResult(false, false,"no correct response from Strava") // do not retry
                    continue
                }

                if (uploadStatusJsonAnswer.has(ERROR) && !uploadStatusJsonAnswer.isNull(ERROR) && uploadStatusJsonAnswer.getString(ERROR) != "null") {
                    // maybe, the error is due to a duplicate.
                    exportResult = checkAndUpdateDuplicate(exportInfo, uploadStatusJsonAnswer)
                    if (exportResult != null) break

                    exportResult = ExportResult(false, false, uploadStatusJsonAnswer.getString(ERROR)) // do not retry
                } else if (uploadStatusJsonAnswer.has(STATUS)) {
                    val status = uploadStatusJsonAnswer.getString(STATUS)
                    if (DEBUG) Log.d(TAG, "strava response status: $status")

                    // when the upload was successfull, we have to update some fields.
                    stravaUploadDbHelper.updateStatus(exportInfo.fileBaseName, status)

                    when (status) {
                        STATUS_PROCESSING -> { /* continue waiting */ }
                        STATUS_DELETED -> exportResult = ExportResult(false, false,STATUS_DELETED) // do not retry
                        STATUS_ERROR -> {
                            // maybe, the error is due to a duplicate.
                            exportResult = checkAndUpdateDuplicate(exportInfo, uploadStatusJsonAnswer)
                            if (exportResult != null) break

                            exportResult = ExportResult(false, false, uploadStatusJsonAnswer.optString(ERROR, "Unknown Error")) // do not retry
                        }
                        STATUS_READY -> {
                            val activityId = uploadStatusJsonAnswer.optString(ACTIVITY_ID)
                            if (!activityId.isNullOrEmpty()) {
                                stravaUploadDbHelper.updateActivityId(exportInfo.fileBaseName, activityId)
                                exportResult = doUpdate(exportInfo)
                            } else {
                                if (DEBUG) Log.e(TAG, "Status ready but no activity_id?")
                                exportResult = ExportResult(true, false, getPositiveAnswer(exportInfo))  // success -> no need for retry
                            }
                        }
                    }
                }
            }
            return exportResult ?: ExportResult(false, true,"Timeout waiting for Strava processing")  // timeout -> retry
        }
        return ExportResult(false, false, "Unknown response format from Strava")  // do not retry
    }

    private fun checkAndUpdateDuplicate(exportInfo: ExportInfo, stravaJson: JSONObject): ExportResult? {
        if (DEBUG) Log.d(TAG, "checkAndHandleDuplicate")

        // Handles both:
        // 1. "duplicate of <a href='\/activities\/16877339482"
        // 2. "duplicate of activity 119487747"

        if (stravaJson.has(ERROR)) {
            val id = stravaJson.optString(ID)
            val error = stravaJson.getString(ERROR)

            val regex = "duplicate of.*?(?:activity|activities)\\D+(\\d+)".toRegex(RegexOption.IGNORE_CASE)
            val matchResult = regex.find(error)

            if (matchResult != null) {
                // groupValues[1] contains the ID from the (\d+) capture group
                val activityId = matchResult.groupValues[1]

                if (DEBUG) Log.i(TAG, "activity_id=$activityId")

                StravaUploadDbHelper(mContext).updateAll(
                    exportInfo.fileBaseName,
                    id,
                    activityId,
                    error
                )

                return doUpdate(exportInfo)
            }
        }

        return null
    }

    protected fun doUpdate(exportInfo: ExportInfo): ExportResult {
        if (DEBUG) Log.d(TAG, "doUpdate: ${exportInfo.fileBaseName}")

        val activityId = StravaUploadDbHelper(mContext).getActivityId(exportInfo.fileBaseName)
        if (activityId.isNullOrEmpty()) {
            return ExportResult(true, false, "${getPositiveAnswer(exportInfo)} (Update skipped: No Activity ID)")  // no retry
        }

        // Get Summary from DB
        val dbManager = WorkoutSummariesDatabaseManager.getInstance()
        val db = dbManager.openDatabase
        val cursor = db.query(
            WorkoutSummaries.TABLE, null,
            "${WorkoutSummaries.FILE_BASE_NAME}=?",
            arrayOf(exportInfo.fileBaseName), null, null, null
        )

        if (!cursor.moveToFirst()) {
            cursor.close()
            dbManager.closeDatabase()
            return ExportResult(false, false, "Could not find workout summary")  // not retry
        }

        val sportId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID))
        val sportName = SportTypeDatabaseManager.getStravaName(sportId)
        val name = myGetStringFromCursor(cursor, WorkoutSummaries.WORKOUT_NAME)
        val description = myGetStringFromCursor(cursor, WorkoutSummaries.DESCRIPTION)
        val trainer = myGetBooleanFromCursor(cursor, WorkoutSummaries.TRAINER)
        val commute = myGetBooleanFromCursor(cursor, WorkoutSummaries.COMMUTE)

        val eqIndex = cursor.getColumnIndex(WorkoutSummaries.EQUIPMENT_ID)
        val gearId: String? = if (!cursor.isNull(eqIndex)) {
            EquipmentDbHelper(mContext).getStravaIdFromId(cursor.getInt(eqIndex))
        } else null

        cursor.close()
        dbManager.closeDatabase()


        // First of all, we have to update the sport type.  Thereby, query Strava several times to make sure that the sport type is correct.
        // In the past, we had problems when updating the sport type and the gear in one step.
        updateStravaActivity(activityId, FormBody.Builder().add(TYPE, sportName).build())
        var activityJSON: JSONObject? = getStravaActivity(activityId) ?: return ExportResult(false, false,"updating Strava failed (get)")  // no retry

        var waitingTime = INITIAL_WAITING_TIME
        for (attempt in 1..MAX_REQUESTS) {
            if (activityJSON != null && sportName.equals(activityJSON?.optString(TYPE), ignoreCase = true)) {
                break
            }

            waitingTime = (waitingTime * INCREASE_WAITING_TIME_MULT).toLong()
            Thread.sleep(waitingTime)
            activityJSON = getStravaActivity(activityId)
        }

        // Now, that we are pretty sure that the sport type is correct, we can continue to update all other fields.


        // Prepare Form Body for metadata update
        val formBuilder = FormBody.Builder()

        if (!name.isNullOrEmpty()) {
            formBuilder.add(NAME, name)
        }
        if (!gearId.isNullOrEmpty()) {
            formBuilder.add(GEAR_ID, gearId)
        }
        if (!description.isNullOrEmpty()) {
            formBuilder.add(DESCRIPTION, description)
        }
        formBuilder.add(TRAINER, trainer.toString())
        formBuilder.add(COMMUTE, commute.toString())

        // update the activity
        activityJSON = updateStravaActivity(activityId, formBuilder.build())
            ?: return ExportResult(false, false,"Update request failed") // no retry

        if (DEBUG) Log.i(TAG, "Update Result: $activityJSON")

        return ExportResult(true, false, "successfully updated")  // success -> no retry necessary

        // TODO: Verify???
    }

    private fun updateStravaActivity(stravaActivityId: String, requestBody: RequestBody): JSONObject? {
        if (DEBUG) Log.i(TAG, "updateStravaActivity $stravaActivityId")

        val request = Request.Builder()
            .url(URL_STRAVA_ACTIVITY + stravaActivityId)
            .put(requestBody)
            .addHeader("Authorization", "Bearer ${StravaHelper.getRefreshedAccessToken()}")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    JSONObject(response.body!!.string())
                } else {
                    Log.e(TAG, "Update failed: ${response.code} ${response.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating activity", e)
            null
        }
    }

    private fun getStravaActivity(stravaActivityId: String): JSONObject? {
        return getStravaJson(URL_STRAVA_ACTIVITY + stravaActivityId)
    }

    private fun getStravaUploadStatus(uploadId: String): JSONObject? {
        return getStravaJson("$URL_STRAVA_UPLOAD/$uploadId")
    }

    private fun getStravaJson(url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer ${StravaHelper.getRefreshedAccessToken()}")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    JSONObject(response.body!!.string())
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching JSON from $url", e)
            null
        }
    }

    // Helper to replace "myGetStringFromCursor"
    override fun myGetStringFromCursor(cursor: Cursor, columnName: String): String? {
        val index = cursor.getColumnIndex(columnName)
        return if (index != -1 && !cursor.isNull(index)) cursor.getString(index) else null
    }

    // Helper to replace "myGetBooleanFromCursor"
    override fun myGetBooleanFromCursor(cursor: Cursor, columnName: String): Boolean {
        val index = cursor.getColumnIndex(columnName)
        return if (index != -1 && !cursor.isNull(index)) cursor.getInt(index) == 1 else false
    }
}