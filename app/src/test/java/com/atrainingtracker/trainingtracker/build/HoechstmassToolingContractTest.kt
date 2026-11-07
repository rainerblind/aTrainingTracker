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

package com.atrainingtracker.trainingtracker.build

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the parameterized Agile Agentic ASPICE (AAA) tooling export under tools/hoechstmass/tools/
 * (declarative configuration, .env.jira template, jira_util.py, verify_requirement_governance.py,
 * and Python unit tests) (REQ-PRO-036, TST-PRO-036, ATT-5667).
 */
class HoechstmassToolingContractTest {

    private fun findRepoRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate $relativePath in working directory: ${File(".").absolutePath}")
    }

    @Test
    fun testDeclarativeConfigurationSchema_existsAndIsValidJson() {
        val configFile = findRepoRootFile("tools/hoechstmass/hoechstmass_config.json")
        assertTrue("hoechstmass_config.json must exist", configFile.exists())
        assertTrue("hoechstmass_config.json must have non-zero size", configFile.length() > 100L)

        val json = JSONObject(configFile.readText())
        assertTrue("Config must declare project_key", json.has("project_key"))
        assertTrue("Config must declare transitions", json.has("transitions"))
        assertTrue("Config must declare roles", json.has("roles"))
        assertTrue("Config must declare custom_fields", json.has("custom_fields"))
        assertTrue("Config must declare requirements", json.has("requirements"))
        assertTrue("Config must declare paths", json.has("paths"))

        val transitions = json.getJSONObject("transitions")
        assertTrue("Transitions must declare todo", transitions.has("todo"))
        assertTrue("Transitions must declare in_progress", transitions.has("in_progress"))
        assertTrue("Transitions must declare in_review", transitions.has("in_review"))
        assertTrue("Transitions must declare done", transitions.has("done"))

        val envTemplate = findRepoRootFile("tools/hoechstmass/.env.jira.example")
        assertTrue(".env.jira.example must exist", envTemplate.exists())
        val envContent = envTemplate.readText()
        assertTrue("Env template must contain JIRA_URL", envContent.contains("JIRA_URL="))
        assertTrue("Env template must contain JIRA_AGENT1_USER", envContent.contains("JIRA_AGENT1_USER="))
        assertTrue("Env template must contain JIRA_AGENT2_USER", envContent.contains("JIRA_AGENT2_USER="))
        assertTrue("Env template must contain JIRA_COORDINATOR_USER", envContent.contains("JIRA_COORDINATOR_USER="))
        assertTrue("Env template must contain JIRA_HUMAN_ACCOUNT_ID", envContent.contains("JIRA_HUMAN_ACCOUNT_ID="))
    }

    @Test
    fun testParameterizedTooling_existsAndIsDecoupled() {
        val jiraUtil = findRepoRootFile("tools/hoechstmass/tools/jira_util.py")
        assertTrue("Parameterized jira_util.py must exist", jiraUtil.exists())
        assertTrue("Parameterized jira_util.py must have content", jiraUtil.length() > 500L)

        val jiraContent = jiraUtil.readText()
        assertFalse("Parameterized jira_util.py must not hardcode 'project = ATT'", jiraContent.contains("project = ATT"))
        assertFalse("Parameterized jira_util.py must not hardcode proprietary developer account ID",
            jiraContent.contains("712020:63ce5f53-e2ba-43f5-9879-edf4a22ff748"))
        assertTrue("Parameterized jira_util.py must support submit-stage", jiraContent.contains("submit_stage") || jiraContent.contains("submit-stage"))
        assertTrue("Parameterized jira_util.py must support gate-verdict", jiraContent.contains("gate_verdict") || jiraContent.contains("gate-verdict"))
        assertTrue("Parameterized jira_util.py must support check-gate", jiraContent.contains("check_gate") || jiraContent.contains("check-gate"))
        assertTrue("Parameterized jira_util.py must support mask_secret", jiraContent.contains("mask_secret"))

        val govUtil = findRepoRootFile("tools/hoechstmass/tools/verify_requirement_governance.py")
        assertTrue("Parameterized verify_requirement_governance.py must exist", govUtil.exists())
        assertTrue("Parameterized verify_requirement_governance.py must have content", govUtil.length() > 500L)

        val govContent = govUtil.readText()
        assertFalse("Parameterized verify_requirement_governance.py must not hardcode proprietary IDs",
            govContent.contains("712020:63ce5f53"))
        assertTrue("Parameterized verify_requirement_governance.py must validate archaeology",
            govContent.contains("validate_archaeology_section"))
        assertTrue("Parameterized verify_requirement_governance.py must check master table parity",
            govContent.contains("verify_master_table_parity"))
    }

    @Test
    fun testPythonToolingUnitTests_passViaExecutionContract() {
        val testFile = findRepoRootFile("tools/hoechstmass/tools/test_hoechstmass_tools.py")
        assertTrue("test_hoechstmass_tools.py must exist", testFile.exists())

        val toolsDir = testFile.parentFile
        val pb = ProcessBuilder("python3", "-m", "unittest", "discover", "-s", ".", "-p", "test_*.py")
        pb.directory(toolsDir)
        val proc = pb.start()
        val exitCode = proc.waitFor()
        assertEquals("Python unit tests must pass with exit code 0", 0, exitCode)
    }

    @Test
    fun testActiveRootTooling_remainsUntouched() {
        val rootJiraUtil = findRepoRootFile("tools/jira_util.py")
        assertTrue("Root tools/jira_util.py must exist", rootJiraUtil.exists())

        val rootGovUtil = findRepoRootFile("tools/verify_requirement_governance.py")
        assertTrue("Root tools/verify_requirement_governance.py must exist", rootGovUtil.exists())
    }
}
