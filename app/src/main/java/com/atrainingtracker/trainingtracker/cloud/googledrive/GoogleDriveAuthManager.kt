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

import android.content.Context
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Authentication and OAuth2 Bearer token manager for Google Drive cloud integration.
 *
 * Enforces least-privilege scope: https://www.googleapis.com/auth/drive.file
 * Guarantees that only Google Play Services verified tokens are persisted.
 */
object GoogleDriveAuthManager {
    private const val TAG = "GoogleDriveAuthManager"
    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val OAUTH2_SCOPE_PREFIX = "oauth2:"

    /**
     * Builds and returns standard GoogleSignInOptions configured for email and drive.file scope.
     */
    fun getSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_FILE_SCOPE))
            .build()
    }

    /**
     * Returns GoogleSignInClient configured for this application.
     */
    fun getClient(context: Context): GoogleSignInClient {
        return GoogleSignIn.getClient(context, getSignInOptions())
    }

    /**
     * Acquires a verified OAuth2 Bearer token for the given signed-in account.
     * Must be invoked on Dispatchers.IO.
     */
    suspend fun acquireBearerToken(context: Context, account: GoogleSignInAccount): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val accountObj = account.account
                    ?: return@withContext Result.failure(IllegalStateException("Google account object is null"))
                val scope = "$OAUTH2_SCOPE_PREFIX$DRIVE_FILE_SCOPE"
                val token = GoogleAuthUtil.getToken(context, accountObj, scope)
                val email = account.email ?: accountObj.name ?: "Google Drive User"
                TrainingApplication.storeGoogleDriveCredential(email, token)
                TrainingApplication.setUploadToGoogleDrive(true)
                Log.d(TAG, "Successfully acquired and stored Google Drive Bearer token for $email")
                Result.success(token)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acquire Google Drive Bearer token", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Refreshes the Bearer token using the last signed in account.
     * Clears the cached token and fetches a fresh one from GoogleAuthUtil.
     */
    suspend fun refreshToken(context: Context): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                    ?: return@withContext Result.failure(IllegalStateException("No signed-in Google account found"))
                val accountObj = account.account
                    ?: return@withContext Result.failure(IllegalStateException("Google account object is null"))
                val oldToken = TrainingApplication.getGoogleDriveAuthToken()
                if (!oldToken.isNullOrBlank()) {
                    try {
                        GoogleAuthUtil.clearToken(context, oldToken)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to clear expired token", e)
                    }
                }
                val scope = "$OAUTH2_SCOPE_PREFIX$DRIVE_FILE_SCOPE"
                val newToken = GoogleAuthUtil.getToken(context, accountObj, scope)
                val email = account.email ?: accountObj.name ?: "Google Drive User"
                TrainingApplication.storeGoogleDriveCredential(email, newToken)
                Result.success(newToken)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh Google Drive Bearer token", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Disconnects the athlete's Google Drive account:
     * Signs out, revokes access, clears cached token, and deletes stored credentials.
     */
    suspend fun disconnect(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val token = TrainingApplication.getGoogleDriveAuthToken()
                if (!token.isNullOrBlank()) {
                    try {
                        GoogleAuthUtil.clearToken(context, token)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to clear GoogleAuthUtil token on disconnect", e)
                    }
                }
                val client = getClient(context)
                client.signOut()
                client.revokeAccess()
            } catch (e: Exception) {
                Log.w(TAG, "Exception during Google Sign-In disconnect", e)
            } finally {
                TrainingApplication.deleteGoogleDriveCredential()
                Log.d(TAG, "Google Drive disconnected and credentials deleted.")
            }
        }
    }
}
