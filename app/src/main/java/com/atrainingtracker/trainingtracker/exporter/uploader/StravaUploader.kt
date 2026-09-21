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

package com.atrainingtracker.trainingtracker.exporter.uploader

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.BaseExporter
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.StravaActivityParser
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

open class StravaUploader @JvmOverloads constructor(context: Context, internal var client: OkHttpClient = OkHttpClient()) : BaseExporter(context) {

    companion object {
        private const val TAG = "StravaUploader"
        private val DEBUG = TrainingApplication.getDebug(true)

        private const val URL_STRAVA_UPLOAD = "https://www.strava.com/api/v3/uploads"
        private const val URL_STRAVA_ACTIVITY = "https://www.strava.com/api/v3/activities/"
        private const val URL_STRAVA_ATHLETE_ACTIVITIES = "https://www.strava.com/api/v3/athlete/activities"
        private const val URL_STRAVA_GEAR = "https://www.strava.com/api/v3/gear/"

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
        private const val SPORT_TYPE = "sport_type"

        // Strava Status messages
        private const val STATUS_PROCESSING = "Your activity is still being processed."
        private const val STATUS_DELETED = "The created activity has been deleted."
        private const val STATUS_ERROR = "There was an error processing your activity."
        private const val STATUS_READY = "Your activity is ready."

        // ATT-1105 / REQ-EXP-008: Strava default name patterns across all supported languages
        private val EMOJI_CLEANUP_REGEX = """[\p{So}\p{Sk}\u2600-\u27BF\uFE00-\uFE0F]|\uD83C[\uDF00-\uDFFF]|\uD83D[\uDC00-\uDE4F]|\uD83E[\uDD00-\uDFFF]""".toRegex()

        // German
        private const val GERMAN_SPORTS = "(?:Radfahrt|Fahrt|Ausfahrt|Rennradfahrt|Mountainbike-Fahrt|Gravel-Fahrt|E-Bike-Fahrt|Lauf|Dauerlauf|Wanderung|Spaziergang|Training|Schwimmen|Workout|Aktivität|Krafttraining|Yoga|Rollerski)"
        private const val GERMAN_TIME_OF_DAY = "(?:am\\s+(?:Morgen|Vormittag|Mittag|Nachmittag|Abend)|in\\s+der\\s+Nacht|nachts)"
        private val GERMAN_DEFAULT_PATTERN_1 = "^$GERMAN_SPORTS\\s+$GERMAN_TIME_OF_DAY$".toRegex(RegexOption.IGNORE_CASE)

        private const val GERMAN_TIME_PREFIX = "(?:Morgen|Vormittags?|Mittags?|Nachmittags?|Abend|Nacht)"
        private val GERMAN_DEFAULT_PATTERN_2 = "^$GERMAN_TIME_PREFIX(?:-|\\s+)$GERMAN_SPORTS$".toRegex(RegexOption.IGNORE_CASE)

        // English
        private const val ENGLISH_TIME_PREFIX = "(?:Morning|Lunch|Afternoon|Evening|Night)"
        private const val ENGLISH_SPORTS = "(?:Ride|Run|Walk|Hike|Swim|Workout|Activity|Weight\\s+Training|Weight\\s+Session|Gravel\\s+Ride|Mountain\\s+Bike\\s+Ride|E-Bike\\s+Ride|Virtual\\s+Ride|Virtual\\s+Run|Row|Rowing|Paddle|Yoga)"
        private val ENGLISH_DEFAULT_PATTERN = "^$ENGLISH_TIME_PREFIX\\s+$ENGLISH_SPORTS$".toRegex(RegexOption.IGNORE_CASE)

        // French
        private const val FRENCH_SPORTS = "(?:Sortie\\s+vélo|Course\\s+à\\s+pied|Course|Marche|Randonnée|Natation|Entraînement|Activité)"
        private const val FRENCH_TIME = "(?:le\\s+matin|en\\s+matinée|à\\s+midi|l'après-midi|en\\s+soirée|le\\s+soir|la\\s+nuit)"
        private val FRENCH_DEFAULT_PATTERN = "^$FRENCH_SPORTS\\s+$FRENCH_TIME$".toRegex(RegexOption.IGNORE_CASE)

        // Spanish
        private const val SPANISH_SPORTS = "(?:Salida\\s+(?:en\\s+)?(?:bicicleta|bici)|Carrera|Paseo|Caminata|Ruta\\s+a\\s+pie|Ruta\\s+en\\s+(?:bici|bicicleta)|Natación|Entrenamiento|Actividad)"
        private const val SPANISH_TIME = "(?:por\\s+la\\s+(?:mañana|tarde|noche)|al\\s+mediodía|del\\s+mediodía)"
        private val SPANISH_DEFAULT_PATTERN_1 = "^$SPANISH_SPORTS\\s+$SPANISH_TIME$".toRegex(RegexOption.IGNORE_CASE)
        private const val SPANISH_ADJECTIVE = "(?:matutin[ao]|vespertin[ao]|nocturn[ao])"
        private val SPANISH_DEFAULT_PATTERN_2 = "^(?:Salida|Carrera|Paseo|Caminata|Ruta|Natación|Entrenamiento|Actividad)\\s+$SPANISH_ADJECTIVE$".toRegex(RegexOption.IGNORE_CASE)

        // Italian
        private const val ITALIAN_SPORTS = "(?:Giro|Corsa|Camminata|Nuotata|Passeggiata|Escursione|Attività|Allenamento)"
        private const val ITALIAN_ADJECTIVES = "(?:mattutin[oa]|pomeridian[oa]|serale|notturn[oa])"
        private val ITALIAN_DEFAULT_PATTERN_1 = "^$ITALIAN_SPORTS\\s+$ITALIAN_ADJECTIVES$".toRegex(RegexOption.IGNORE_CASE)
        private const val ITALIAN_TIME = "(?:del\\s+mattino|del\\s+pomeriggio|della\\s+sera|di\\s+notte|a\\s+pranzo|di\\s+mezzogiorno)"
        private val ITALIAN_DEFAULT_PATTERN_2 = "^$ITALIAN_SPORTS\\s+$ITALIAN_TIME$".toRegex(RegexOption.IGNORE_CASE)

        // Portuguese
        private const val PORTUGUESE_SPORTS = "(?:Pedalada|Corrida|Caminhada|Trilha|Natação|Treino|Atividade)"
        private const val PORTUGUESE_ADJECTIVES = "(?:matinal|vespertin[ao]|noturn[ao])"
        private val PORTUGUESE_DEFAULT_PATTERN_1 = "^$PORTUGUESE_SPORTS\\s+$PORTUGUESE_ADJECTIVES$".toRegex(RegexOption.IGNORE_CASE)
        private const val PORTUGUESE_TIME = "(?:de\\s+manhã|à\\s+tarde|ao\\s+meio-dia|à\\s+noite)"
        private val PORTUGUESE_DEFAULT_PATTERN_2 = "^$PORTUGUESE_SPORTS\\s+$PORTUGUESE_TIME$".toRegex(RegexOption.IGNORE_CASE)

        // Dutch
        private const val DUTCH_TIME_PREFIX = "(?:Ochtend|Middag|Namiddag|Avond|Nacht|Lunch)"
        private const val DUTCH_SPORTS = "(?:rit|fietstocht|loop|hardloopsessie|wandeling|zwemsessie|training|workout|activiteit)"
        private val DUTCH_DEFAULT_PATTERN = "^$DUTCH_TIME_PREFIX(?:-|\\s+)?$DUTCH_SPORTS$".toRegex(RegexOption.IGNORE_CASE)

        // Polish
        private const val POLISH_ADJECTIVES = "(?:Porann[ay]|Popołudniow[ay]|Wieczorn[ay]|Nocn[ay]|Południow[ay])"
        private const val POLISH_SPORTS = "(?:jazda(?:\\s+na\\s+rowerze)?|bieg|spacer|wędrówka|trening|pływanie|aktywność)"
        private val POLISH_DEFAULT_PATTERN = "^$POLISH_ADJECTIVES\\s+$POLISH_SPORTS$".toRegex(RegexOption.IGNORE_CASE)

        // Japanese
        private const val JAPANESE_TIME_PREFIX = "(?:朝|午前|昼|午後|夕方|夜|ナイト)"
        private const val JAPANESE_SPORTS = "(?:サイクリング|ライド|ラン|ウォーク|ウォーキング|ハイキング|スイム|ワークアウト|アクティビティ|トレーニング)"
        private val JAPANESE_DEFAULT_PATTERN = "^$JAPANESE_TIME_PREFIX(?:の)?$JAPANESE_SPORTS$".toRegex(RegexOption.IGNORE_CASE)

        /**
         * Determines whether a given Strava activity name corresponds to an auto-generated
         * localized default title across all supported languages (German, English, French,
         * Spanish, Italian, Portuguese, Dutch, Polish, Japanese).
         *
         * @param name The activity name returned from Strava.
         * @return True if the name matches a known Strava auto-generated default template, false if custom.
         */
        fun isDefaultStravaName(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val cleaned = name.replace(EMOJI_CLEANUP_REGEX, "").trim()
            return GERMAN_DEFAULT_PATTERN_1.matches(cleaned) ||
                   GERMAN_DEFAULT_PATTERN_2.matches(cleaned) ||
                   ENGLISH_DEFAULT_PATTERN.matches(cleaned) ||
                   FRENCH_DEFAULT_PATTERN.matches(cleaned) ||
                   SPANISH_DEFAULT_PATTERN_1.matches(cleaned) ||
                   SPANISH_DEFAULT_PATTERN_2.matches(cleaned) ||
                   ITALIAN_DEFAULT_PATTERN_1.matches(cleaned) ||
                   ITALIAN_DEFAULT_PATTERN_2.matches(cleaned) ||
                   PORTUGUESE_DEFAULT_PATTERN_1.matches(cleaned) ||
                   PORTUGUESE_DEFAULT_PATTERN_2.matches(cleaned) ||
                   DUTCH_DEFAULT_PATTERN.matches(cleaned) ||
                   POLISH_DEFAULT_PATTERN.matches(cleaned) ||
                   JAPANESE_DEFAULT_PATTERN.matches(cleaned)
        }
    }

    override fun doExport(exportInfo: ExportInfo): ExportResult {
        Log.i(TAG, "doExport: fileBaseName=${exportInfo.fileBaseName}, shortPath=${exportInfo.shortPath}")

        val file = File(getBaseDirFile(mContext), exportInfo.shortPath)
        val accessToken = StravaHelper.getRefreshedAccessToken()

        if (accessToken.isNullOrEmpty()) {
            Log.e(TAG, "doExport failed: Strava access token is missing or refresh failed.")
            return ExportResult(false, false, "Could not refresh Strava Access Token. Please log in again.")
        }
        Log.i(TAG, "Strava access token verified.")

        // ATT-1105 / REQ-EXP-008: Check if activity is already recorded in local Strava DB
        val stravaUploadDbHelper = StravaUploadDbHelper(mContext)
        val existingDbActivityId = stravaUploadDbHelper.getActivityId(exportInfo.fileBaseName)
        if (!existingDbActivityId.isNullOrEmpty()) {
            Log.i(TAG, "Activity already tracked locally as Strava ID $existingDbActivityId. Testing update.")
            val updateResult = doUpdate(exportInfo, isDuplicate = true)
            if (updateResult.success()) {
                Log.i(TAG, "Local Strava activity $existingDbActivityId updated successfully.")
                return updateResult
            }
            // If updating failed (e.g. deleted activity on Strava returning 404),
            // the locally cached activityId is stale. Clear stale record and fall through to pre-upload duplicate discovery!
            Log.w(TAG, "Local Strava activity $existingDbActivityId could not be updated (${updateResult.answer()}). Clearing stale record and searching Strava.")
            stravaUploadDbHelper.deleteWorkout(exportInfo.fileBaseName)
        }

        // ATT-1105 / REQ-EXP-008: Pre-upload duplicate discovery
        // Query Strava to see if an activity already exists around the workout's start time.
        val existingStravaActivity = findExistingStravaActivityForWorkout(exportInfo.fileBaseName)
        if (existingStravaActivity != null) {
            val activityId = existingStravaActivity.optString(ID).ifBlank { existingStravaActivity.optString("id") }
            if (activityId.isNotBlank()) {
                Log.i(TAG, "Found pre-existing Strava activity $activityId ('${existingStravaActivity.optString(NAME)}') for ${exportInfo.fileBaseName}. Bypassing TCX upload.")
                StravaUploadDbHelper(mContext).updateAll(
                    exportInfo.fileBaseName,
                    activityId,
                    activityId,
                    "Pre-existing activity found on Strava",
                    existingStravaActivity.toString()
                )
                return doUpdate(exportInfo, isDuplicate = true)
            }
        }

        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "File to upload does not exist or is empty: ${file.absolutePath} (exists=${file.exists()}, length=${file.length()})")
            return ExportResult(false, false, "File to upload does not exist or is empty: ${file.name}")
        }

        Log.i(TAG, "Starting to upload to Strava: ${file.name} (${file.length()} bytes)")

        // 1. Build Multipart Request
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.Companion.FORM)
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
            Log.e(TAG, "Network error uploading to Strava: ${e.message}", e)
            return ExportResult(false, true, "Network error: ${e.message}")  // a network error -> retry
        }

        Log.i(TAG, "uploadToStrava responseCode=$responseCode, response: $responseBody")

        if (responseBody.isEmpty()) {
            return ExportResult(false, false, "no response from Strava")  // probably something strange -> do not retry
        }

        // 3. Handle Errors
        if (responseCode != 201 && responseCode != 200) {
            Log.w(TAG, "Bad response code from Strava: $responseCode")
            return ExportResult(false, false, "API Error: $responseBody")  // probably something strange -> do not retry
        }

        // 4. Handle Success (Initial Upload)
        Log.i(TAG, "Successfully uploaded to STRAVA, checking result")

        val uploadResponseJson = JSONObject(responseBody)

        val initialDuplicate = checkAndUpdateDuplicate(exportInfo, uploadResponseJson)
        if (initialDuplicate != null) {
            return initialDuplicate
        }

        if (uploadResponseJson.has(ERROR) && !uploadResponseJson.isNull(ERROR) && uploadResponseJson.getString(ERROR) != "null") {
            return ExportResult(false, false, uploadResponseJson.getString(ERROR))  // probably something strange -> do not retry
        } else if (uploadResponseJson.has(ID)) {
            val uploadId = uploadResponseJson.getString(ID)
            val stravaUploadDbHelper = StravaUploadDbHelper(mContext)

            stravaUploadDbHelper.updateUploadId(exportInfo.fileBaseName, uploadId)
            stravaUploadDbHelper.updateStatus(exportInfo.fileBaseName, "Uploaded to STRAVA, checking result")

            // 5. Poll for processing status
            var exportResult: ExportResult? = null
            var waitingTime = INITIAL_WAITING_TIME

            for (attempt in 1..MAX_REQUESTS) {
                if (exportResult != null) break
                Thread.sleep(waitingTime)
                waitingTime = (waitingTime * INCREASE_WAITING_TIME_MULT).toLong()

                val uploadStatusJsonAnswer = getStravaUploadStatus(uploadId)
                if (uploadStatusJsonAnswer == null) {
                    Log.w(TAG, "Attempt $attempt: getStravaUploadStatus returned null for uploadId=$uploadId")
                    exportResult = ExportResult(false, false,"no correct response from Strava") // do not retry
                    continue
                }

                // Check for duplicate response across error or status fields first
                val duplicateResult = checkAndUpdateDuplicate(exportInfo, uploadStatusJsonAnswer)
                if (duplicateResult != null) {
                    Log.i(TAG, "Duplicate detected during polling for uploadId=$uploadId")
                    exportResult = duplicateResult
                    break
                }

                if (uploadStatusJsonAnswer.has(ERROR) && !uploadStatusJsonAnswer.isNull(ERROR) && uploadStatusJsonAnswer.getString(ERROR) != "null") {
                    val err = uploadStatusJsonAnswer.getString(ERROR)
                    Log.w(TAG, "Strava reported error during upload $uploadId: $err")
                    exportResult = ExportResult(false, false, err) // do not retry
                } else if (uploadStatusJsonAnswer.has(STATUS)) {
                    val status = uploadStatusJsonAnswer.getString(STATUS)
                    Log.i(TAG, "Polling attempt $attempt for uploadId=$uploadId: status='$status'")

                    // when the upload was successfull, we have to update some fields.
                    stravaUploadDbHelper.updateStatus(exportInfo.fileBaseName, status)

                    when (status) {
                        STATUS_PROCESSING -> { /* continue waiting */ }
                        STATUS_DELETED -> {
                            Log.w(TAG, "Strava activity deleted during processing for uploadId=$uploadId")
                            exportResult = ExportResult(false, false, STATUS_DELETED)
                        }
                        STATUS_ERROR -> {
                            val err = uploadStatusJsonAnswer.optString(ERROR, "Unknown Error")
                            Log.w(TAG, "Strava activity error for uploadId=$uploadId: $err")
                            exportResult = ExportResult(false, false, err)
                        }
                        STATUS_READY -> {
                            val activityId = uploadStatusJsonAnswer.optString(ACTIVITY_ID)
                            Log.i(TAG, "Strava activity is ready! uploadId=$uploadId, activityId=$activityId")
                            if (!activityId.isNullOrEmpty()) {
                                stravaUploadDbHelper.updateActivityId(exportInfo.fileBaseName, activityId)
                                exportResult = doUpdate(exportInfo, isDuplicate = false)
                            } else {
                                Log.e(TAG, "Status ready but no activity_id?")
                                exportResult = ExportResult(true, false, "Status ready but no activity_id?")  // success -> no need for retry
                            }
                        }
                    }
                }
            }
            return exportResult ?: ExportResult(false, true,"Timeout waiting for Strava processing")  // timeout -> retry
        }
        return ExportResult(false, false, "Unknown response format from Strava")  // do not retry
    }

    internal open fun checkAndUpdateDuplicate(exportInfo: ExportInfo, stravaJson: JSONObject): ExportResult? {
        // Handles variants like:
        // 1. "duplicate of <a href='\/activities\/16877339482"
        // 2. "duplicate of activity 119487747"
        // 3. "activity.tcx duplicate of 119487747"
        // Whether returned in 'error' or 'status' fields.

        val errorMsg = if (stravaJson.has(ERROR) && !stravaJson.isNull(ERROR)) stravaJson.optString(ERROR) else ""
        val statusMsg = if (stravaJson.has(STATUS) && !stravaJson.isNull(STATUS)) stravaJson.optString(STATUS) else ""
        val combinedText = "$errorMsg $statusMsg"

        val regex = "duplicate of.*?(\\d+)".toRegex(RegexOption.IGNORE_CASE)
        val matchResult = regex.find(combinedText)

        if (matchResult != null) {
            // groupValues[1] contains the ID from the (\d+) capture group
            val activityId = matchResult.groupValues[1]
            val id = stravaJson.optString(ID)
            val errorOrStatus = if (errorMsg.isNotBlank() && errorMsg != "null") errorMsg else statusMsg

            Log.i(TAG, "checkAndUpdateDuplicate: Found duplicate of activityId=$activityId, combinedText='$combinedText'")

            StravaUploadDbHelper(mContext).updateAll(
                exportInfo.fileBaseName,
                id,
                activityId,
                errorOrStatus,
                stravaJson.toString()
            )

            return doUpdate(exportInfo, isDuplicate = true)
        }

        return null
    }

    internal open fun doUpdate(exportInfo: ExportInfo, isDuplicate: Boolean = false): ExportResult {
        Log.i(TAG, "doUpdate: fileBaseName=${exportInfo.fileBaseName}, isDuplicate=$isDuplicate")

        val activityId = StravaUploadDbHelper(mContext).getActivityId(exportInfo.fileBaseName)
        if (activityId.isNullOrEmpty()) {
            Log.w(TAG, "doUpdate skipped: No Activity ID for ${exportInfo.fileBaseName}")
            return ExportResult(true, false, "Update skipped: No Activity ID")  // no retry
        }

        // Get Summary from DB
        val dbManager = WorkoutSummariesDatabaseManager.getInstance(mContext)
        val db = dbManager.database
        val cursor = db.query(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE, null,
            "${WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME}=?",
            arrayOf(exportInfo.fileBaseName), null, null, null
        )

        if (!cursor.moveToFirst()) {
            Log.w(TAG, "doUpdate failed: Could not find workout summary for ${exportInfo.fileBaseName}")
            cursor.close()
            return ExportResult(false, false, "Could not find workout summary")  // not retry
        }

        val sportId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.SPORT_ID))
        val sportName = SportTypeDatabaseManager.getInstance(mContext).getStravaName(sportId)
        
        if (sportName == null) {
            Log.i(TAG, "doUpdate skipped: Sport mapping set to 'No upload' (sportId=$sportId)")
            cursor.close()
            return ExportResult(true, false, "Update skipped: Sport mapping set to 'No upload'")
        }

        val name = myGetStringFromCursor(cursor, WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME)
        val description = myGetStringFromCursor(cursor, WorkoutSummariesDatabaseManager.WorkoutSummaries.DESCRIPTION)
        val trainer = myGetBooleanFromCursor(cursor, WorkoutSummariesDatabaseManager.WorkoutSummaries.TRAINER)
        val commute = myGetBooleanFromCursor(cursor, WorkoutSummariesDatabaseManager.WorkoutSummaries.COMMUTE)

        val eqIndex = cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.EQUIPMENT_ID)
        val gearId: String? = if (!cursor.isNull(eqIndex)) {
            val eqId = cursor.getLong(eqIndex)
            getGearId(eqId)
        } else null

        cursor.close()


        // First of all, we have to update the sport type.  Thereby, query Strava several times to make sure that the sport type is correct.
        // In the past, we had problems when updating the sport type and the gear in one step.
        // Modern Strava API uses sport_type, though type still works for some legacy types. We send both for maximum compatibility.
        // For duplicates, we preserve Strava's authoritative sport type and cloud metadata.
        if (!isDuplicate) {
            updateStravaActivity(activityId, FormBody.Builder()
                .add(TYPE, sportName)
                .add(SPORT_TYPE, sportName)
                .build())
        }
        var activityJSON: JSONObject? = getStravaActivity(activityId) ?: return ExportResult(false, false,"updating Strava failed (get)")  // no retry

        if (!isDuplicate) {
            var waitingTime = INITIAL_WAITING_TIME
            for (attempt in 1..MAX_REQUESTS) {
                // Check both type and sport_type in the response
                if (activityJSON != null && (
                    sportName.equals(activityJSON.optString(TYPE), ignoreCase = true) ||
                    sportName.equals(activityJSON.optString(SPORT_TYPE), ignoreCase = true)
                )) {
                    break
                }

                waitingTime = (waitingTime * INCREASE_WAITING_TIME_MULT).toLong()
                Thread.sleep(waitingTime)
                activityJSON = getStravaActivity(activityId)
            }
        }
        if (DEBUG) Log.i(TAG, "doUpdate: activityJSON=$activityJSON")

        // SAVE STRAVA ACTIVITY DATA (REQ-EXP-013: minimize to athlete achievements)
        val minimizedData = StravaActivityParser.minimize(activityJSON)
        StravaUploadDbHelper(mContext).updateStravaActivityData(exportInfo.fileBaseName, minimizedData)

        // ATT-912 / REQ-EXP-010: Ingest Strava segment feedback to update local PRs:
        processSegmentEffortsForPrs(activityJSON)

        // ATT-902 / ATT-1105 / ATT-1114: If duplicate, reconcile with Strava metadata:
        if (isDuplicate && activityJSON != null) {
            val updateValues = ContentValues()

            // 1. Name evaluation (ATT-902 / ATT-1105 / REQ-EXP-008):
            // - If Strava title is a default auto-generated name (e.g. "Radfahrt am Morgen", "Lauf am Abend ⛅"):
            //   Keep the local name generated by the app (cluster name or TCX name); do not overwrite.
            // - If Strava title is a custom name (not a Strava default):
            //   Enrich local database with the custom Strava title.
            val stravaName = activityJSON.optString(NAME)
            if (!stravaName.isNullOrBlank() && !isDefaultStravaName(stravaName)) {
                updateValues.put(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME, stravaName)
                if (DEBUG) Log.i(TAG, "Enriched local workout name from custom Strava title: '$stravaName'")
            } else if (isDefaultStravaName(stravaName)) {
                if (DEBUG) Log.i(TAG, "Preserving local app workout name because Strava title is default: '$stravaName'")
            }

            // 2. Equipment evaluation (ATT-1114 / REQ-EXP-009):
            val stravaGearId = activityJSON.optString(GEAR_ID).takeIf { it.isNotBlank() && it != "null" }
                ?: activityJSON.optJSONObject("gear")?.optString("id")?.takeIf { it.isNotBlank() && it != "null" }
            if (stravaGearId != null) {
                val equipmentDb = EquipmentDbHelper(mContext)
                var localEquipmentId = equipmentDb.getIdFromStravaId(stravaGearId)
                if (localEquipmentId <= 0) {
                    val gearJson = getStravaGear(stravaGearId)
                    if (gearJson != null) {
                        val gearName = gearJson.optString(NAME).takeIf { it.isNotBlank() } ?: stravaGearId
                        val frameType = gearJson.optInt("frame_type", 0)
                        val retired = gearJson.optBoolean("retired", false)
                        val sportType = if (stravaGearId.startsWith("b", ignoreCase = true)) {
                            BSportType.BIKE.name
                        } else {
                            BSportType.RUN.name
                        }
                        localEquipmentId = equipmentDb.addOrUpdateStravaGear(
                            stravaGearId,
                            gearName,
                            frameType,
                            sportType,
                            retired
                        )
                    }
                }
                if (localEquipmentId > 0) {
                    updateValues.put(WorkoutSummariesDatabaseManager.WorkoutSummaries.EQUIPMENT_ID, localEquipmentId)
                    if (DEBUG) Log.i(TAG, "Enriched local workout equipmentId: $localEquipmentId from Strava gear: $stravaGearId")
                }
            }

            // 3. Sport type evaluation (ATT-1114 / REQ-EXP-009):
            val stravaSportType = activityJSON.optString(SPORT_TYPE).takeIf { it.isNotBlank() && it != "null" }
                ?: activityJSON.optString(TYPE).takeIf { it.isNotBlank() && it != "null" }
            if (stravaSportType != null) {
                val sportTypeDb = SportTypeDatabaseManager.getInstance(mContext)
                val localSportId = sportTypeDb.getSportTypeIdFromStravaName(stravaSportType)
                if (localSportId > 0) {
                    updateValues.put(WorkoutSummariesDatabaseManager.WorkoutSummaries.SPORT_ID, localSportId)
                    if (DEBUG) Log.i(TAG, "Enriched local workout sportId: $localSportId from Strava sportType: $stravaSportType")
                }
            }

            if (updateValues.size() > 0) {
                db.update(
                    WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                    updateValues,
                    "${WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME}=?",
                    arrayOf(exportInfo.fileBaseName)
                )
            }
        }

        // Now, that we are pretty sure that the sport type is correct, we can continue to update all other fields.
        // Prepare Form Body for metadata update
        val formBuilder = FormBody.Builder()

        // ATT-902 / REQ-EXP-008: Smart Asymmetric Strategy:
        // - If duplicate: NEVER send name (preserve Strava title as authoritative).
        // - If fresh upload: only send name if it is meaningful (not blank and not equal to raw fileBaseName).
        if (!isDuplicate && !name.isNullOrBlank() && name != exportInfo.fileBaseName) {
            formBuilder.add(NAME, name)
        }
        if (!isDuplicate && !gearId.isNullOrEmpty()) {
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

        Log.i(TAG, "doUpdate Result for $activityId: $activityJSON")

        // Final feedback check: Is the activity flagged?
        val isFlagged = activityJSON.optBoolean("flagged", false)
        val message = if (isFlagged) {
            "successfully updated (Note: Activity is FLAGGED on Strava)"
        } else {
            "successfully updated"
        }

        return ExportResult(true, false, message)  // success -> no retry necessary

        // TODO: Verify???
    }

    internal open fun updateStravaActivity(stravaActivityId: String, requestBody: RequestBody): JSONObject? {
        Log.i(TAG, "updateStravaActivity $stravaActivityId")

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

    internal open fun getStravaActivity(stravaActivityId: String): JSONObject? {
        return getStravaJson(URL_STRAVA_ACTIVITY + stravaActivityId)
    }

    internal open fun getStravaGear(gearId: String): JSONObject? {
        return getStravaJson(URL_STRAVA_GEAR + gearId)
    }

    internal open fun getGearId(eqId: Long): String? {
        return if (eqId > 0) EquipmentDbHelper(mContext).getStravaIdFromId(eqId) else null
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

    internal open fun getStravaJsonArray(url: String): JSONArray? {
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer ${StravaHelper.getRefreshedAccessToken()}")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    JSONArray(response.body!!.string())
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching JSONArray from $url", e)
            null
        }
    }

    /**
     * Searches Strava athlete activities for an existing activity matching the workout's start time.
     * Searches a +/- 5-minute (300s) temporal window around the start timestamp.
     */
    internal open fun findExistingStravaActivityForWorkout(fileBaseName: String): JSONObject? {
        val startEpochSeconds = getWorkoutStartEpochSeconds(fileBaseName) ?: return null
        return findExistingStravaActivity(startEpochSeconds)
    }

    internal open fun getWorkoutStartEpochSeconds(fileBaseName: String): Long? {
        val dbManager = WorkoutSummariesDatabaseManager.getInstance(mContext)
        return try {
            val cursor = dbManager.database.query(
                WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                arrayOf(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START),
                "${WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME}=?",
                arrayOf(fileBaseName),
                null, null, null
            )
            val timeStartStr = cursor.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_START)
                    if (idx != -1 && !it.isNull(idx)) {
                        it.getString(idx)
                    } else if (!it.isNull(0)) {
                        it.getString(0)
                    } else null
                } else null
            } ?: fileBaseName

            parseTimeToEpochSeconds(timeStartStr)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query workout start epoch for $fileBaseName", e)
            parseTimeToEpochSeconds(fileBaseName)
        }
    }

    internal open fun parseTimeToEpochSeconds(timeStr: String): Long? {
        // Try ISO 8601 (e.g. "2024-05-12T10:15:30Z")
        try {
            return java.time.Instant.parse(timeStr).epochSecond
        } catch (_: Exception) { }

        // Try SQLite format (e.g. "2024-05-12 10:15:30")
        try {
            val dbFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val parsed = dbFormat.parse(timeStr)?.time?.div(1000)
            if (parsed != null) return parsed
        } catch (_: Exception) { }

        // Try standard fileBaseName format without inner hyphens (e.g. "2024-05-12_101530")
        try {
            val fileFormat1 = java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.ROOT).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val parsed = fileFormat1.parse(timeStr)?.time?.div(1000)
            if (parsed != null) return parsed
        } catch (_: Exception) { }

        // Try fileBaseName format with hyphens (e.g. "2024-05-12_10-15-30")
        return try {
            val fileFormat2 = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.ROOT).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            fileFormat2.parse(timeStr)?.time?.div(1000)
        } catch (_: Exception) {
            null
        }
    }

    internal open fun findExistingStravaActivity(startEpochSeconds: Long): JSONObject? {
        val after = startEpochSeconds - 300L
        val before = startEpochSeconds + 300L
        val url = "$URL_STRAVA_ATHLETE_ACTIVITIES?after=$after&before=$before"
        Log.i(TAG, "findExistingStravaActivity: startEpoch=$startEpochSeconds, querying $url")
        val array = getStravaJsonArray(url)
        if (array == null || array.length() == 0) {
            Log.i(TAG, "findExistingStravaActivity: No activities found in time window (count=${array?.length() ?: 0})")
            return null
        }
        Log.i(TAG, "findExistingStravaActivity: Found ${array.length()} activities in time window")

        var closestActivity: JSONObject? = null
        var minDiff = Long.MAX_VALUE

        for (i in 0 until array.length()) {
            val act = array.optJSONObject(i) ?: continue
            val startDateStr = act.optString("start_date") // e.g. "2024-05-12T08:15:30Z"
            val actEpoch = parseTimeToEpochSeconds(startDateStr) ?: 0L
            val diff = if (actEpoch > 0) Math.abs(actEpoch - startEpochSeconds) else 0L
            if (diff < minDiff) {
                minDiff = diff
                closestActivity = act
            }
        }
        if (closestActivity != null) {
            Log.i(TAG, "findExistingStravaActivity: Closest activity ID=${closestActivity.optString("id")}, name='${closestActivity.optString("name")}', diff=${minDiff}s")
        }
        return closestActivity
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

    /**
     * Inspects segment efforts in Strava activity JSON feedback and updates personal best (PR)
     * times in [SegmentsDatabaseManager] and [SegmentsRepository] for any achieved PR or KOM.
     *
     * @param activityJson The detailed activity JSON returned by Strava.
     */
    internal open fun processSegmentEffortsForPrs(activityJson: JSONObject?) {
        if (activityJson == null) return
        val segmentEffortsArray = activityJson.optJSONArray("segment_efforts") ?: return
        val segmentsDb = SegmentsDatabaseManager.getInstance(mContext)
        val segmentsRepo = SegmentsRepository.getInstance(mContext)

        for (i in 0 until segmentEffortsArray.length()) {
            val effort = segmentEffortsArray.optJSONObject(i) ?: continue
            val elapsedTime = effort.optInt("elapsed_time", 0)
            if (elapsedTime <= 0) continue

            val segmentObj = effort.optJSONObject("segment")
            val segmentId = if (segmentObj != null && segmentObj.has("id") && !segmentObj.isNull("id")) {
                segmentObj.optLong("id")
            } else if (effort.has("segment_id") && !effort.isNull("segment_id")) {
                effort.optLong("segment_id")
            } else {
                0L
            }
            if (segmentId <= 0L) continue

            var isNewPr = false
            val prRank = if (effort.has("pr_rank") && !effort.isNull("pr_rank")) effort.optInt("pr_rank") else null
            val komRank = if (effort.has("kom_rank") && !effort.isNull("kom_rank")) effort.optInt("kom_rank") else null

            if (prRank == 1 || komRank == 1) {
                isNewPr = true
            } else {
                effort.optJSONArray("achievements")?.let { achArray ->
                    for (a in 0 until achArray.length()) {
                        val ach = achArray.optJSONObject(a) ?: continue
                        val type = ach.optString("type")
                        val typeId = ach.optInt("type_id", -1)
                        val rank = if (ach.has("rank") && !ach.isNull("rank")) ach.optInt("rank") else 1
                        if (rank == 1 && (type.equals("pr", ignoreCase = true) || type.equals("kom", ignoreCase = true) || type.equals("overall", ignoreCase = true) || typeId == 2 || typeId == 3)) {
                            isNewPr = true
                            break
                        }
                    }
                }
            }

            if (isNewPr) {
                val updated = segmentsDb.updateSegmentPrTime(segmentId, elapsedTime)
                if (updated) {
                    segmentsRepo.updateSegmentPr(segmentId, elapsedTime)
                    if (DEBUG) Log.i(TAG, "processSegmentEffortsForPrs: Updated PR for segment $segmentId to ${elapsedTime}s")
                }
            }
        }
    }
}