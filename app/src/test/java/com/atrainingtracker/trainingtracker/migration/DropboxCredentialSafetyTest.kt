/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import android.content.SharedPreferences
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.dropbox.core.oauth.DbxCredential
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit tests verifying null safety and resilience of Dropbox credential reading.
 * Fulfills REQ-MIG-023 / TST-MIG-020 for ATT-491.
 */
class DropboxCredentialSafetyTest {

    private fun setSharedPreferences(prefs: SharedPreferences?) {
        val field: Field = TrainingApplication::class.java.getDeclaredField("cSharedPreferences")
        field.isAccessible = true
        field.set(null, prefs)
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        setSharedPreferences(null)
        unmockkStatic(Log::class)
    }

    @Test
    fun testNullSharedPreferencesDoesNotCrash() {
        setSharedPreferences(null)
        val result = TrainingApplication.readDropboxCredential()
        assertNull(result)
    }

    @Test
    fun testNullCredentialStringDoesNotCrash() {
        val mockPrefs = mockk<SharedPreferences>()
        every { mockPrefs.getString("dropboxCredential", null) } returns null
        setSharedPreferences(mockPrefs)

        val result = TrainingApplication.readDropboxCredential()
        assertNull(result)
    }

    @Test
    fun testEmptyOrBlankCredentialStringDoesNotCrash() {
        val mockPrefs = mockk<SharedPreferences>()
        every { mockPrefs.getString("dropboxCredential", null) } returns ""
        setSharedPreferences(mockPrefs)
        assertNull(TrainingApplication.readDropboxCredential())

        every { mockPrefs.getString("dropboxCredential", null) } returns "   \n  "
        assertNull(TrainingApplication.readDropboxCredential())
    }

    @Test
    fun testMalformedJsonCredentialStringDoesNotCrash() {
        val mockPrefs = mockk<SharedPreferences>()
        every { mockPrefs.getString("dropboxCredential", null) } returns "{ invalid json content }"
        setSharedPreferences(mockPrefs)

        val result = TrainingApplication.readDropboxCredential()
        assertNull(result)
    }

    @Test
    fun testValidCredentialStringParsesSuccessfully() {
        val cred = DbxCredential("test_token_12345")
        val validJson = DbxCredential.Writer.writeToString(cred)

        val mockPrefs = mockk<SharedPreferences>()
        every { mockPrefs.getString("dropboxCredential", null) } returns validJson
        setSharedPreferences(mockPrefs)

        val result = TrainingApplication.readDropboxCredential()
        assertNotNull(result)
        assertEquals("test_token_12345", result?.accessToken)
    }
}
