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

package com.atrainingtracker.trainingtracker.util

import android.content.Context
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveClient
import com.atrainingtracker.trainingtracker.elevation.ElevationService
import com.atrainingtracker.trainingtracker.exporter.uploader.StravaUploader
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaAuthRepository
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit verification suite for `ATT-6037` (`REQ-UI-2793`, `TST-UI-2793`):
 * - Verifies [AppHttpClient.shared] is a singleton and [AppHttpClient.newBuilder] shares the base
 *   [okhttp3.ConnectionPool] and [okhttp3.Dispatcher] across all derived production clients.
 * - Verifies custom timeouts in [StravaHelper], [ElevationService], and [GoogleDriveClient] are
 *   strictly preserved alongside constructor/setter test injections (`StravaUploader`,
 *   `StravaHelper`, `ElevationService`, `GoogleDriveClient`).
 */
class AppHttpClientPoolSharingTest {

    @After
    fun tearDown() {
        StravaHelper.resetHttpClient()
    }

    @Test
    fun testSharedBaseClient_returnsSingletonAndDerivedClientsSharePoolAndDispatcher_tstUi2793_3() {
        val base1 = AppHttpClient.shared
        val base2 = AppHttpClient.shared
        assertSame(base1, base2)

        val derived = AppHttpClient.newBuilder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()

        assertSame("Derived client must share ConnectionPool", base1.connectionPool, derived.connectionPool)
        assertSame("Derived client must share Dispatcher", base1.dispatcher, derived.dispatcher)
        assertEquals(7_000, derived.connectTimeoutMillis)
        assertEquals(12_000, derived.readTimeoutMillis)
    }

    @Test
    fun testProductionClients_shareBasePoolPreserveCustomTimeoutsAndTestInjections_tstUi2793_3() {
        val shared = AppHttpClient.shared

        // 1. StravaHelper: derived client with 15s connect, 15s read, 20s call timeout + setter injection
        StravaHelper.resetHttpClient()
        val stravaClient = StravaHelper.httpClient
        assertSame(shared.connectionPool, stravaClient.connectionPool)
        assertSame(shared.dispatcher, stravaClient.dispatcher)
        assertEquals(15_000, stravaClient.connectTimeoutMillis)
        assertEquals(15_000, stravaClient.readTimeoutMillis)
        assertEquals(20_000, stravaClient.callTimeoutMillis)

        val injectedStrava = OkHttpClient()
        StravaHelper.httpClient = injectedStrava
        assertSame("StravaHelper.httpClient test setter must be preserved", injectedStrava, StravaHelper.httpClient)
        StravaHelper.resetHttpClient()
        assertSame(stravaClient, StravaHelper.httpClient)

        // 2. ElevationService: derived client with 5s connect/read/write timeout + constructor injection
        val elevationService = ElevationService.getInstance()
        val clientField = ElevationService::class.java.getDeclaredField("client").apply { isAccessible = true }
        val elevationClient = clientField.get(elevationService) as OkHttpClient
        assertSame(shared.connectionPool, elevationClient.connectionPool)
        assertSame(shared.dispatcher, elevationClient.dispatcher)
        assertEquals(5_000, elevationClient.connectTimeoutMillis)
        assertEquals(5_000, elevationClient.readTimeoutMillis)
        assertEquals(5_000, elevationClient.writeTimeoutMillis)

        val injectedElevation = OkHttpClient()
        val customElevationService = ElevationService(injectedElevation)
        assertSame("ElevationService constructor injection must be preserved", injectedElevation, clientField.get(customElevationService))

        // 3. GoogleDriveClient: derived client with 30s connect, 60s read/write timeout + constructor injection
        val driveClient = GoogleDriveClient(tokenProvider = { null }).client
        assertSame(shared.connectionPool, driveClient.connectionPool)
        assertSame(shared.dispatcher, driveClient.dispatcher)
        assertEquals(30_000, driveClient.connectTimeoutMillis)
        assertEquals(60_000, driveClient.readTimeoutMillis)
        assertEquals(60_000, driveClient.writeTimeoutMillis)

        val injectedDrive = OkHttpClient()
        val customDriveClient = GoogleDriveClient(tokenProvider = { null }, client = injectedDrive)
        assertSame("GoogleDriveClient constructor injection must be preserved", injectedDrive, customDriveClient.client)

        // 4. StravaUploader: defaults to AppHttpClient.shared + preserves constructor injection
        val mockContext = mockk<Context>(relaxed = true)
        val defaultUploader = StravaUploader(mockContext)
        assertSame(shared, defaultUploader.client)

        val injectedUploaderClient = OkHttpClient()
        val customUploader = StravaUploader(mockContext, injectedUploaderClient)
        assertSame("StravaUploader constructor injection must be preserved", injectedUploaderClient, customUploader.client)

        // 5. StravaAuthRepository: defaults to AppHttpClient.shared
        assertSame(shared, StravaAuthRepository.getInstance().client)
    }
}
