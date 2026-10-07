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

import com.atrainingtracker.R
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class GoogleDriveAuthErrorMappingTest {

    @Test
    fun testResolveErrorMessage_null_returnsNull() {
        val resId = GoogleDriveAuthErrorResolver.resolveErrorMessageResId(null)
        assertNull(resId)
    }

    @Test
    fun testResolveErrorMessage_status10DeveloperError_returnsDeveloperConfigStringRes() {
        val ex = ApiException(Status(CommonStatusCodes.DEVELOPER_ERROR))
        val resId = GoogleDriveAuthErrorResolver.resolveErrorMessageResId(ex)
        assertEquals(R.string.google_drive_error_developer_config, resId)
    }

    @Test
    fun testResolveErrorMessage_status7NetworkError_returnsNetworkStringRes() {
        val ex = ApiException(Status(CommonStatusCodes.NETWORK_ERROR))
        val resId = GoogleDriveAuthErrorResolver.resolveErrorMessageResId(ex)
        assertEquals(R.string.google_drive_error_network, resId)
    }

    @Test
    fun testResolveErrorMessage_signInCancelled_returnsNull() {
        val ex = ApiException(Status(GoogleSignInStatusCodes.SIGN_IN_CANCELLED))
        val resId = GoogleDriveAuthErrorResolver.resolveErrorMessageResId(ex)
        assertNull(resId)
    }

    @Test
    fun testResolveErrorMessage_unmappedStatusCode_returnsGenericStringRes() {
        val ex = ApiException(Status(GoogleSignInStatusCodes.SIGN_IN_FAILED))
        val resId = GoogleDriveAuthErrorResolver.resolveErrorMessageResId(ex)
        assertEquals(R.string.google_drive_error_generic, resId)
    }

    @Test
    fun testResolveErrorMessage_internalErrorStatusCode_returnsGenericStringRes() {
        val ex = ApiException(Status(CommonStatusCodes.INTERNAL_ERROR))
        val resId = GoogleDriveAuthErrorResolver.resolveErrorMessageResId(ex)
        assertEquals(R.string.google_drive_error_generic, resId)
    }

    @Test
    fun testResolveErrorMessage_nonApiException_returnsGenericStringRes() {
        val ex = IOException("Connection reset by peer")
        val resId = GoogleDriveAuthErrorResolver.resolveErrorMessageResId(ex)
        assertEquals(R.string.google_drive_error_generic, resId)
    }
}
