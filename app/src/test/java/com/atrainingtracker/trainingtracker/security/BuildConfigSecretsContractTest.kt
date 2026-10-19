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

package com.atrainingtracker.trainingtracker.security

import com.atrainingtracker.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.util.Properties

/**
 * Contract test verifying strict BuildConfig secret exposure restrictions,
 * keystore credential exclusion, dead third-party secret pruning, and default properties
 * configuration (REQ-SEC-042 / TST-SEC-039).
 */
class BuildConfigSecretsContractTest {

    companion object {
        /**
         * Exhaustive allow-list of permissible fields in [BuildConfig].
         * Any field present in bytecode that is not in this set triggers an immediate build failure.
         */
        private val ALLOWED_BUILD_CONFIG_FIELDS = setOf(
            // Standard Android Gradle Plugin (AGP) fields
            "DEBUG",
            "APPLICATION_ID",
            "BUILD_TYPE",
            "VERSION_CODE",
            "VERSION_NAME",
            // Legitimate external service credentials
            "MAPS_API_KEY",
            "STRAVA_CLIENT_ID",
            "STRAVA_CLIENT_SECRET",
            "DROPBOX_APP_KEY"
        )

        private val REQUIRED_SECRET_FIELDS = setOf(
            "MAPS_API_KEY",
            "STRAVA_CLIENT_ID",
            "STRAVA_CLIENT_SECRET",
            "DROPBOX_APP_KEY"
        )

        private val FORBIDDEN_SECRET_NAMES = listOf(
            "DROPBOX_APP_SECRET",
            "RUNKEEPER_CLIENT_ID",
            "RUNKEEPER_CLIENT_SECRET",
            "TRAININGPEAKS_CLIENT_ID",
            "TRAININGPEAKS_CLIENT_SECRET"
        )
    }

    /**
     * TST-SEC-039.1: Verify BuildConfig declared fields strictly adhere to the allow-list schema.
     * Prevents accidental "fail-open" exposure of any newly introduced properties.
     */
    @Test
    fun testBuildConfig_containsOnlyAllowedFields() {
        val declaredFieldNames = BuildConfig::class.java.declaredFields.map { it.name }.toSet()

        val unapprovedFields = declaredFieldNames - ALLOWED_BUILD_CONFIG_FIELDS
        assertTrue(
            "Unapproved field(s) detected in BuildConfig: $unapprovedFields. " +
                "Only explicitly approved fields in ALLOWED_BUILD_CONFIG_FIELDS are permitted!",
            unapprovedFields.isEmpty()
        )
    }

    /**
     * TST-SEC-039.1: Verify that all required external API keys are present and non-blank.
     */
    @Test
    fun testRequiredBuildConfigFields_arePresentAndNonBlank() {
        val declaredFields = BuildConfig::class.java.declaredFields.associateBy { it.name }

        for (requiredField in REQUIRED_SECRET_FIELDS) {
            val field = declaredFields[requiredField]
            assertNotNull("Mandatory field '$requiredField' missing from BuildConfig", field)

            field?.isAccessible = true
            val value = field?.get(null) as? String
            assertNotNull("Value for '$requiredField' must not be null", value)
            assertTrue("Value for '$requiredField' must not be blank", value!!.isNotBlank())
        }
    }

    /**
     * TST-SEC-039.2: Verify that decommissioned client secrets (Runkeeper, TrainingPeaks, Dropbox App Secret)
     * are completely absent from BuildConfig declared fields.
     */
    @Test
    fun testDecommissionedSecrets_areExcludedFromBuildConfig() {
        val declaredFieldNames = BuildConfig::class.java.declaredFields.map { it.name }

        for (forbiddenName in FORBIDDEN_SECRET_NAMES) {
            assertFalse(
                "Decommissioned secret '$forbiddenName' must NOT be present in BuildConfig!",
                declaredFieldNames.contains(forbiddenName)
            )
        }

        // Broad regex check for any lingering Runkeeper or TrainingPeaks variants
        for (fieldName in declaredFieldNames) {
            assertFalse(
                "Runkeeper field '$fieldName' must not exist in BuildConfig",
                fieldName.startsWith("RUNKEEPER_", ignoreCase = true)
            )
            assertFalse(
                "TrainingPeaks field '$fieldName' must not exist in BuildConfig",
                fieldName.startsWith("TRAININGPEAKS_", ignoreCase = true)
            )
        }
    }

    /**
     * TST-SEC-039.3: Verify that keystore paths, aliases, and signing passwords from release.properties
     * are NEVER compiled into BuildConfig bytecode.
     */
    @Test
    fun testKeystoreAndSigningCredentials_areNeverPresentInBuildConfig() {
        val declaredFieldNames = BuildConfig::class.java.declaredFields.map { it.name }

        val signingPattern = Regex("(?i).*key(alias|store|password).*")
        for (fieldName in declaredFieldNames) {
            assertFalse(
                "Keystore/signing credential field '$fieldName' detected in BuildConfig! " +
                    "Signing credentials must be excluded via ignoreList.",
                signingPattern.matches(fieldName)
            )
        }
    }

    /**
     * TST-SEC-039.4: Verify that secrets.defaults.properties exists, contains ONLY required keys,
     * and strictly omits all dead/decommissioned secrets.
     */
    @Test
    fun testSecretsDefaultsFile_containsOnlyRequiredKeys() {
        var dir: File? = File(".").canonicalFile
        var defaultsFile: File? = null
        while (dir != null) {
            val candidate = File(dir, "secrets.defaults.properties")
            if (candidate.exists()) {
                defaultsFile = candidate
                break
            }
            dir = dir.parentFile
        }

        assertNotNull("secrets.defaults.properties must exist at project root", defaultsFile)
        assertTrue("secrets.defaults.properties must be a file", defaultsFile!!.isFile)

        val props = Properties()
        FileInputStream(defaultsFile).use { props.load(it) }

        val loadedKeys = props.stringPropertyNames()

        // Assert all required keys are defined
        for (required in REQUIRED_SECRET_FIELDS) {
            assertTrue("secrets.defaults.properties must define '$required'", loadedKeys.contains(required))
            val value = props.getProperty(required)
            assertTrue("Default value for '$required' must not be blank", value.isNotBlank())
        }

        // Assert no forbidden/decommissioned keys exist in defaults
        for (forbidden in FORBIDDEN_SECRET_NAMES) {
            assertFalse(
                "secrets.defaults.properties must NOT contain decommissioned key '$forbidden'",
                loadedKeys.contains(forbidden)
            )
        }

        // Assert exact key parity (zero extraneous keys)
        val extraKeys = loadedKeys - REQUIRED_SECRET_FIELDS
        assertTrue(
            "secrets.defaults.properties contains unexpected keys: $extraKeys. Only required keys are allowed!",
            extraKeys.isEmpty()
        )
    }
}
