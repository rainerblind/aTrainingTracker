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

import androidx.annotation.StringRes
import com.atrainingtracker.R
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

/**
 * Pure mapping utility resolving Google Sign-In and token acquisition exceptions
 * to localized string resource identifiers.
 */
object GoogleDriveAuthErrorResolver {

    /**
     * Resolves a localized string resource ID for user-facing error presentation.
     *
     * @param throwable The caught exception from Google Sign-In or token retrieval.
     * @return String resource ID if an error should be presented to the user, or null if cancelled / no error.
     */
    @StringRes
    fun resolveErrorMessageResId(throwable: Throwable?): Int? {
        if (throwable == null) {
            return null
        }

        if (throwable is ApiException) {
            return when (throwable.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> null
                CommonStatusCodes.DEVELOPER_ERROR -> R.string.google_drive_error_developer_config
                CommonStatusCodes.NETWORK_ERROR -> R.string.google_drive_error_network
                else -> R.string.google_drive_error_generic
            }
        }

        return R.string.google_drive_error_generic
    }
}
