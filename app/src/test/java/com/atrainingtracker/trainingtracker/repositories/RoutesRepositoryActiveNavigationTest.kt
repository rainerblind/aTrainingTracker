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

package com.atrainingtracker.trainingtracker.repositories

import android.content.Context
import android.util.Log
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying active navigation state tracking in RoutesRepository (REQ-MAP-023 / TST-MAP-025 / ATT-1841).
 */
class RoutesRepositoryActiveNavigationTest {

    private lateinit var mockContext: Context
    private lateinit var mockRoutesDb: RoutesDatabaseManager
    private lateinit var repository: RoutesRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        mockRoutesDb = mockk(relaxed = true)
        every { mockRoutesDb.getAllRoutes() } returns emptyList()

        repository = RoutesRepository(mockContext, mockRoutesDb)
    }

    @Test
    fun testActiveNavigatedRouteId_defaultsToNull() {
        assertNull(repository.activeNavigatedRouteId.value)
    }

    @Test
    fun testSetActiveNavigatedRoute_updatesStateFlow() {
        repository.setActiveNavigatedRoute(42L)
        assertEquals(42L, repository.activeNavigatedRouteId.value)

        repository.setActiveNavigatedRoute(100L)
        assertEquals(100L, repository.activeNavigatedRouteId.value)

        repository.setActiveNavigatedRoute(null)
        assertNull(repository.activeNavigatedRouteId.value)
    }

    @After
    fun tearDown() {
        repository.cancelScope()
        unmockkAll()
    }
}
