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

package com.atrainingtracker.trainingtracker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Basic instrumented smoke test verifying application context bootstrap and package id
 * under AndroidJUnit4 on an Android runtime / emulator (REQ-PRO-051.2, TST-PRO-051.3).
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeAndroidTest {

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull("Target context must not be null", appContext)

        val packageName = appContext.packageName
        assertTrue(
            "Package name must be either debug or release application id. Actual: $packageName",
            packageName == "com.atrainingtracker" || packageName == "com.atrainingtracker.debug"
        )
    }

    @Test
    fun testInstrumentationBootstrap() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertNotNull("Instrumentation registry must provide valid instrumentation", instrumentation)
        assertNotNull("Instrumentation context must be non-null", instrumentation.context)
    }
}
