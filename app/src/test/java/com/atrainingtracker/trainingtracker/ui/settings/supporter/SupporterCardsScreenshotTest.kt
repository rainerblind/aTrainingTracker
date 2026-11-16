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

package com.atrainingtracker.trainingtracker.ui.settings.supporter

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.monetization.GarageLeague
import com.atrainingtracker.trainingtracker.monetization.SupporterBasePlan
import com.atrainingtracker.trainingtracker.monetization.SupporterTier
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Rule 8.5 Visual Compose Screenshot verification for ATT-8108 (TST-SET-117.5, REQ-SET-115.4).
 *
 * Renders Light and Dark comparisons of all 5 [SupporterPlanCard] tiers side-by-side:
 * - Free user with Gold-qualified garage (Silver Recommended anchor + Gold Garage-Match badge)
 * - Bronze user with Diamond-qualified garage (Bronze Active + Silver Recommended + Diamond Garage-Match badge)
 */
// Linked test specs: TST-SET-117
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h1400dp-xhdpi")
class SupporterCardsScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testCaptureSupporterCardsComparisonScreenshot() {
        // TST-SET-117.5
        composeTestRule.setContent {
            Column(
                modifier = Modifier
                    .width(920.dp)
                    .background(Color(0xFF888888))
                    .padding(8.dp)
                    .testTag("supporter_cards_comparison"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SupporterCardsPreviewRow(
                            themeTitle = "Light Theme (Gold Garage / Silver Active + Diamond Match)"
                        )
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SupporterCardsPreviewRow(
                            themeTitle = "Dark Theme (Gold Garage / Silver Active + Diamond Match)"
                        )
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("supporter_cards_comparison")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8108_supporter_cards.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot must exist", outFile.exists())
        assertTrue("Screenshot must be non-empty", outFile.length() > 1024L)
    }

    @Composable
    private fun SupporterCardsPreviewRow(themeTitle: String) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = themeTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SupporterCardsColumn(
                    label = "Free + Gold Garage",
                    effectiveTier = SupporterTier.FREE,
                    qualifiedLeague = GarageLeague.GOLD,
                    modifier = Modifier.weight(1f)
                )
                SupporterCardsColumn(
                    label = "Silver + Diamond Garage",
                    effectiveTier = SupporterTier.SILVER,
                    qualifiedLeague = GarageLeague.DIAMOND,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    @Composable
    private fun SupporterCardsColumn(
        label: String,
        effectiveTier: SupporterTier,
        qualifiedLeague: GarageLeague,
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            SupporterBasePlan.PLANS.forEach { plan ->
                SupporterPlanCard(
                    plan = plan,
                    isCurrentTier = effectiveTier == plan.tier,
                    effectiveTier = effectiveTier,
                    isPreSelected = false,
                    isGarageMatch = isGarageMatchForPlan(plan, qualifiedLeague, effectiveTier),
                    onSelectPlan = {}
                )
            }
        }
    }
}
