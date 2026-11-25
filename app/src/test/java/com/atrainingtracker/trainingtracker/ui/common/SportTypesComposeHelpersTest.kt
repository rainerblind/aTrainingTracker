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

package com.atrainingtracker.trainingtracker.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.repositories.SimpleSportTypeInfo
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral Compose UI test for [rememberSportTypesList] verifying immediate recomposition
 * when [SportTypesRepository.sportTypesChanged] emits (`REQ-SET-134.3`, `TST-SET-134.3`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SportTypesComposeHelpersTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockRepo: SportTypesRepository

    @Before
    fun setUp() {
        mockRepo = mockk(relaxed = true)
        SportTypesRepository.resetForTesting(mockRepo)
    }

    @After
    fun tearDown() {
        SportTypesRepository.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testRememberSportTypesList_recomposesImmediatelyOnSportTypesChanged_TST_SET_134_3() {
        every { mockRepo.getSportTypes(BSportType.UNKNOWN) } returns listOf(
            SimpleSportTypeInfo(1L, "Other", BSportType.UNKNOWN),
            SimpleSportTypeInfo(3L, "Bike", BSportType.BIKE)
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                val sportTypes = rememberSportTypesList()
                Column {
                    sportTypes.forEach { item ->
                        Text(text = item.name)
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Bike").assertIsDisplayed()
        composeTestRule.onNodeWithText("Swimming").assertDoesNotExist()

        every { mockRepo.getSportTypes(BSportType.UNKNOWN) } returns listOf(
            SimpleSportTypeInfo(1L, "Other", BSportType.UNKNOWN),
            SimpleSportTypeInfo(3L, "Bike", BSportType.BIKE),
            SimpleSportTypeInfo(6L, "Swimming", BSportType.UNKNOWN)
        )
        SportTypesRepository.notifySportTypesChanged()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Swimming").assertIsDisplayed()
    }
}
