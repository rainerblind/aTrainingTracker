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

package com.atrainingtracker.banalservice.ui.devices.editdevice

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Instrumented visual verification test rendering [PolarBackupRecordingCard] for Polar Verity Sense
 * on an Android emulator (`ATT-7923`, `REQ-SET-108.1`, `TST-SET-110.5`).
 */
@RunWith(AndroidJUnit4::class)
class EditDevicePolarBackupAndroidTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    @Test
    fun testRenderPolarVerityBackupCardsAndCaptureScreenshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        var composeContainer: ComposeView? = null

        ActivityScenario.launch(MainActivityWithNavigation::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val composeView = ComposeView(activity).apply {
                    setContent {
                        ATrainingTrackerTheme(setWindowColors = false) {
                            Surface(color = MaterialTheme.colorScheme.background) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .statusBarsPadding()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "Polar Sense A1B2C3D4",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    PolarBackupRecordingCard(
                                        checked = true,
                                        isVeritySense = true,
                                        isAndroid13Plus = true,
                                        onCheckedChange = {}
                                    )
                                    PolarBackupRecordingCard(
                                        checked = true,
                                        isVeritySense = true,
                                        isAndroid13Plus = false,
                                        onCheckedChange = {}
                                    )
                                }
                            }
                        }
                    }
                }
                composeContainer = composeView
                activity.setContentView(
                    composeView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }

            instrumentation.waitForIdleSync()
            SystemClock.sleep(600)

            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val outDir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
            val outFile = File(outDir, "att7923_verity_backup_card.png")
            FileOutputStream(outFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }

            assertTrue("Rendered ComposeView must be laid out", (composeContainer?.height ?: 0) > 0)
            assertTrue("Captured screenshot must exist", outFile.exists() && outFile.length() > 1024L)
        }
    }
}
