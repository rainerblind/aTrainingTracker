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

package com.atrainingtracker.trainingtracker.settings

import android.app.Activity
import android.app.Application
import android.content.Context
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.monetization.ProFeatureManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.reflect.Modifier

/**
 * Lifecycle, constructor encapsulation, applicationContext normalization, scope reuse, and
 * test-override pinning tests for [SettingsDataStore],
 * [MyPreferenceManager], and [ProFeatureManager] (REQ-SET-128 / TST-SET-130 / ATT-8121).
 */
// Linked test specs: TST-SET-130
class PreferenceSingletonsLifecycleTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockActivity = mockk<Activity>(relaxed = true)
    private val mockWorkoutDbManager = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)

    @Before
    fun setUp() {
        every { mockApplication.applicationContext } returns mockApplication
        every { mockApplication.filesDir } returns tempFolder.newFolder("app_files_dir")
        every { mockActivity.applicationContext } returns mockApplication

        SettingsDataStore.setInstanceForTesting(null)
        MyPreferenceManager.setInstanceForTesting(null)
        ProFeatureManager.setInstanceForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
    }

    @After
    fun tearDown() {
        SettingsDataStore.setInstanceForTesting(null)
        MyPreferenceManager.setInstanceForTesting(null)
        ProFeatureManager.setInstanceForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
    }

    @Test
    fun testSettingsDataStore_constructorIsPrivateAndNormalizesActivityContext() {
        // TST-SET-130.1
        assertNoPublicNonSyntheticConstructors(SettingsDataStore::class.java)

        val fromActivity = SettingsDataStore.getInstance(mockActivity)
        val fromApp = SettingsDataStore.getInstance(mockApplication)

        assertSame(fromActivity, fromApp)

        val storedContext = readDeclaredField<Context>(fromActivity, "context")
        assertSame(mockApplication, storedContext)
        assertFalse(storedContext is Activity)
    }

    @Test
    fun testMyPreferenceManager_constructorIsPrivateSharesProcessScopeAndReusesSingleton() {
        // TST-SET-130.1, TST-SET-130.2
        assertNoPublicNonSyntheticConstructors(MyPreferenceManager::class.java)

        val fromActivity = MyPreferenceManager.getInstance(mockActivity)
        val fromApp = MyPreferenceManager.getInstance(mockApplication)
        assertSame(fromActivity, fromApp)

        val instanceA = MyPreferenceManager.createForTesting(mockActivity)
        val instanceB = MyPreferenceManager.createForTesting(mockApplication)

        val sharedScopeA = readDeclaredField<CoroutineScope>(instanceA, "appScope")
        val sharedScopeB = readDeclaredField<CoroutineScope>(instanceB, "appScope")
        assertSame(sharedScopeA, sharedScopeB)
        assertSame(sharedScopeA.coroutineContext[Job], sharedScopeB.coroutineContext[Job])

        // Cancelling an explicitly injected testScope must never affect sharedAppScope
        val explicitTestScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val scopedInstance = MyPreferenceManager.createForTesting(
            context = mockApplication,
            appScope = explicitTestScope
        )
        assertSame(explicitTestScope, readDeclaredField<CoroutineScope>(scopedInstance, "appScope"))
        explicitTestScope.cancel()
        assertFalse(explicitTestScope.isActive)
        assertTrue(sharedScopeA.isActive)
    }

    @Test
    fun testSettingsDataStore_createForTestingNormalizesContextAndSupportsTestScopeCancellation() {
        // TST-SET-130.2
        val explicitTestScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = SettingsDataStore.createForTesting(
            context = mockActivity,
            dataStore = InMemoryPreferencesDataStore(),
            testScope = explicitTestScope
        )

        val storedContext = readDeclaredField<Context>(store, "context")
        assertSame(mockApplication, storedContext)
        assertFalse(storedContext is Activity)
        assertSame(explicitTestScope, readDeclaredField<CoroutineScope>(store, "appScope"))

        explicitTestScope.cancel()
        assertFalse(explicitTestScope.isActive)
    }

    @Test
    fun testProFeatureManager_constructorIsPrivateNormalizesActivityContextAndPreservesJvmStatic() {
        // TST-SET-130.1
        assertNoPublicNonSyntheticConstructors(ProFeatureManager::class.java)

        val getInstanceMethod = ProFeatureManager::class.java.getMethod("getInstance", Context::class.java)
        assertTrue(
            "ProFeatureManager.getInstance(Context) must remain @JvmStatic for Java callers",
            Modifier.isStatic(getInstanceMethod.modifiers)
        )

        WorkoutSummariesDatabaseManager.setInstanceForTesting(mockWorkoutDbManager)
        val mockSettings = mockk<SettingsDataStore>(relaxed = true)
        SettingsDataStore.setInstanceForTesting(mockSettings)

        val fromActivity = ProFeatureManager.getInstance(mockActivity)
        val fromApp = ProFeatureManager.getInstance(mockApplication)
        assertSame(fromActivity, fromApp)

        val storedContext = readDeclaredField<Context>(fromActivity, "context")
        assertSame(mockApplication, storedContext)
        assertFalse(storedContext is Activity)

        val explicitTestScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val testManager = ProFeatureManager.createForTesting(
                context = mockActivity,
                coroutineScope = explicitTestScope,
                isDebugBuild = false
            )
            assertSame(mockApplication, readDeclaredField<Context>(testManager, "context"))
        } finally {
            explicitTestScope.cancel()
        }
    }

    @Test
    fun testSettingsDataStore_setInstanceForTestingPinsOverrideUntilCleared() {
        // TST-SET-130.3
        val pinnedTestScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val pinned = SettingsDataStore.createForTesting(
                context = mockApplication,
                dataStore = InMemoryPreferencesDataStore(),
                testScope = pinnedTestScope
            )
            SettingsDataStore.setInstanceForTesting(pinned)
            assertSame(pinned, SettingsDataStore.getInstance(mockActivity))
            assertSame(pinned, SettingsDataStore.getInstance(mockApplication))
        } finally {
            pinnedTestScope.cancel()
        }
    }

    @Test
    fun testMyPreferenceManager_setInstanceForTestingPinsOverrideUntilCleared() {
        // TST-SET-130.3
        val pinned = MyPreferenceManager.createForTesting(mockApplication, InMemoryPreferencesDataStore())
        MyPreferenceManager.setInstanceForTesting(pinned)
        assertSame(pinned, MyPreferenceManager.getInstance(mockActivity))
        assertSame(pinned, MyPreferenceManager.getInstance(mockApplication))
    }

    private fun assertNoPublicNonSyntheticConstructors(clazz: Class<*>) {
        for (constructor in clazz.declaredConstructors) {
            if (constructor.isSynthetic) continue
            assertFalse(
                "${clazz.simpleName} constructor $constructor must not be public",
                Modifier.isPublic(constructor.modifiers)
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> readDeclaredField(target: Any, fieldName: String): T {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target) as T
    }
}
