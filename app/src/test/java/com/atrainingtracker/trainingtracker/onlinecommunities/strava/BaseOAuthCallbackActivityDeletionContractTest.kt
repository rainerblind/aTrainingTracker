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

package com.atrainingtracker.trainingtracker.onlinecommunities.strava

import org.junit.Assert.fail
import org.junit.Test

/**
 * Asserts that the dead, insecure legacy BaseOAuthCallbackActivity has been permanently
 * removed from the codebase (REQ-EXT-012.5, TST-EXT-009.12, ATT-4022).
 */
class BaseOAuthCallbackActivityDeletionContractTest {

    @Test
    fun testBaseOAuthCallbackActivity_hasBeenPermanentlyDeleted() {
        try {
            Class.forName("com.atrainingtracker.trainingtracker.onlinecommunities.BaseOAuthCallbackActivity")
            fail("BaseOAuthCallbackActivity must not exist in the codebase")
        } catch (e: ClassNotFoundException) {
            // Expected - class has been successfully pruned
        }
    }
}
