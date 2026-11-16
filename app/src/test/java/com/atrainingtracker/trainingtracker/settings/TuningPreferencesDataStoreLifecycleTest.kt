/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.settings

import android.app.Activity
import android.app.Application
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural lifecycle and singleton scope verification tests for TuningPreferencesDataStore
 * (REQ-TRK-079.1 / TST-TRK-079.1 / ATT-7874).
 */
class TuningPreferencesDataStoreLifecycleTest {

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockActivity = mockk<Activity>(relaxed = true)

    @Before
    fun setUp() {
        every { mockApplication.applicationContext } returns mockApplication
        every { mockActivity.applicationContext } returns mockApplication
        TuningPreferencesDataStore.setInstanceForTesting(null)
    }

    @After
    fun tearDown() {
        TuningPreferencesDataStore.setInstanceForTesting(null)
    }

    @Test
    fun testTuningPreferencesDataStore_singletonNormalizationAcrossActivityAndApplicationContexts() {
        val fromActivity = TuningPreferencesDataStore.getInstance(mockActivity)
        val fromApp = TuningPreferencesDataStore.getInstance(mockApplication)

        // Must return identical singleton reference
        assertSame(fromActivity, fromApp)

        // Verify stored context is normalized to applicationContext (never the Activity)
        val contextField = TuningPreferencesDataStore::class.java.getDeclaredField("context")
        contextField.isAccessible = true
        val storedContext = contextField.get(fromActivity) as Context

        assertSame(mockApplication, storedContext)
        assertFalse(storedContext is Activity)
    }

    @Test
    fun testTuningPreferencesDataStore_constructorIsNotPublicToPreventScopeLeaks() {
        val constructors = TuningPreferencesDataStore::class.java.declaredConstructors
        for (constructor in constructors) {
            if (constructor.isSynthetic) continue
            val modifiers = constructor.modifiers
            assertFalse(
                "TuningPreferencesDataStore constructor $constructor must not be public to prevent scope leaks",
                Modifier.isPublic(modifiers)
            )
        }
    }
}
