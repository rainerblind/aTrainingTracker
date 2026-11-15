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

package com.atrainingtracker.trainingtracker.ui.aftermath

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Instrumented UI verification rendering [PolarH10RetrySyncBanner] in Polar Verity Sense
 * Blue Sensor Mode vs. Polar H10 mode and capturing a screenshot (`ATT-7924`, `REQ-SET-109.3`, `TST-SET-111.5`).
 */
@RunWith(AndroidJUnit4::class)
class PolarRetrySyncBannerAndroidTest {

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
    fun testRenderPolarVerityRetryBannerAndCaptureScreenshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        var composeContainer: ComposeView? = null

        val verityTitle = appContext.getString(R.string.polar_verity_retry_sync_title)
        val verityDesc = appContext.getString(R.string.polar_verity_retry_sync_desc)
        val h10Title = appContext.getString(R.string.polar_h10_retry_sync_title)
        val h10Desc = appContext.getString(R.string.polar_h10_retry_sync_desc)
        assertTrue(verityTitle.contains("Verity Sense"))
        assertTrue(verityDesc.isNotBlank() && verityDesc != h10Desc)
        assertTrue(verityTitle != h10Title)

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
                                        .padding(vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PolarH10RetrySyncBanner(
                                        onSyncNow = {},
                                        onDismiss = {},
                                        isVeritySense = true
                                    )
                                    PolarH10RetrySyncBanner(
                                        onSyncNow = {},
                                        onDismiss = {},
                                        isVeritySense = true,
                                        errorMessage = appContext.getString(R.string.polar_verity_retry_sync_failed)
                                    )
                                }
                            }
                        }
                    }
                }
                activity.setContentView(
                    composeView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                composeContainer = composeView
            }

            instrumentation.waitForIdleSync()
            SystemClock.sleep(600)

            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val outDir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
            val savedFile = File(outDir, "ATT-7924_verity_retry_banner.png")
            FileOutputStream(savedFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            bitmap.recycle()

            assertTrue("Rendered ComposeView must be laid out", (composeContainer?.height ?: 0) > 0)
            assertTrue("Expected screenshot file ${savedFile.absolutePath}", savedFile.exists() && savedFile.length() > 1000L)
        }
    }
}
