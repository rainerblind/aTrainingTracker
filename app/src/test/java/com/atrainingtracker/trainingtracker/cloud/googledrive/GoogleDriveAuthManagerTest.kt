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
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit tests for [GoogleDriveAuthManager] verifying least-privilege OAuth scope,
 * GoogleSignInOptions construction, and safe credential handling.
 */
class GoogleDriveAuthManagerTest {

    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<Throwable>()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0

        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers {
            val cs = firstArg<CharSequence?>()
            cs == null || cs.isEmpty()
        }

        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor

        setStaticField(TrainingApplication::class.java, "cSharedPreferences", mockPrefs)
    }

    @After
    fun tearDown() {
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
        unmockkAll()
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            val field: Field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (_: Exception) {
        }
    }

    @Test
    fun testScope_isStrictlyLeastPrivilegeDriveFile() {
        assertEquals("https://www.googleapis.com/auth/drive.file", GoogleDriveAuthManager.DRIVE_FILE_SCOPE)
    }

    @Test
    fun testGetSignInOptions_configuresScopeAndEmail() {
        val options = GoogleDriveAuthManager.getSignInOptions()
        assertNotNull(options)

        val scopes = options.scopes
        val expectedScope = Scope(GoogleDriveAuthManager.DRIVE_FILE_SCOPE)
        assertTrue(
            "Expected options scopes to contain ${GoogleDriveAuthManager.DRIVE_FILE_SCOPE}",
            scopes.contains(expectedScope)
        )
    }

    @Test
    fun testAcquireBearerToken_whenAccountIsNull_returnsFailure() = runBlocking {
        val mockContext = mockk<Context>(relaxed = true)
        val mockSignInAccount = mockk<GoogleSignInAccount>()
        every { mockSignInAccount.account } returns null

        val result = GoogleDriveAuthManager.acquireBearerToken(mockContext, mockSignInAccount)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun testDisconnect_wipesCredentialsFromPreferences() = runBlocking {
        val mockContext = mockk<Context>(relaxed = true)
        every { mockPrefs.getString(TrainingApplication.SP_GOOGLE_DRIVE_AUTH_TOKEN, null) } returns null

        GoogleDriveAuthManager.disconnect(mockContext)

        verify { mockEditor.remove(TrainingApplication.SP_GOOGLE_DRIVE_ACCOUNT_EMAIL) }
        verify { mockEditor.remove(TrainingApplication.SP_GOOGLE_DRIVE_AUTH_TOKEN) }
        verify { mockEditor.putBoolean(TrainingApplication.SP_UPLOAD_TO_GOOGLE_DRIVE, false) }
    }
}
