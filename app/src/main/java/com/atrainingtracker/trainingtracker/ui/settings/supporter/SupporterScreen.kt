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

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.monetization.ProFeatureManager
import com.atrainingtracker.trainingtracker.monetization.SupporterBasePlan
import com.atrainingtracker.trainingtracker.monetization.SupporterTier
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import kotlinx.coroutines.launch

/**
 * "Pay what you want" Supporter screen offering 5 subscription base plans,
 * hardware wishlist voting, and Pioneer recognition (REQ-SET-086).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SupporterScreen(
    onNavigateBack: () -> Unit,
    preSelectedPlanId: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsDataStore = remember { SettingsDataStore.getInstance(context) }
    val proFeatureManager = remember { ProFeatureManager.getInstance(context) }

    val effectiveTier by proFeatureManager.effectiveTier.collectAsState()
    val isPioneer by settingsDataStore.isPioneerEarlyAdopter.collectAsState(initial = false)
    val selectedHardwareVote by settingsDataStore.hardwareVote.collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.supporter_screen_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Subtitle / mission
            Text(
                text = stringResource(R.string.supporter_screen_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Pioneer recognition banner
            if (isPioneer || effectiveTier == SupporterTier.PIONEER) {
                PioneerStatusBanner()
                Spacer(modifier = Modifier.height(16.dp))
            } else if (effectiveTier.isPro) {
                ActiveTierBanner(tier = effectiveTier)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Hardware voting section (REQ-SET-086.2)
            HardwareVotingSection(
                selectedOption = selectedHardwareVote,
                onSelectOption = { option ->
                    scope.launch {
                        settingsDataStore.setHardwareVote(option.name)
                        Log.i("SupporterScreen", "Hardware vote cast: ${option.name}")
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Subscription plans section header
            Text(
                text = stringResource(R.string.supporter_screen_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 5 Base plans
            SupporterBasePlan.PLANS.forEach { plan ->
                SupporterPlanCard(
                    plan = plan,
                    isCurrentTier = effectiveTier == plan.tier,
                    isPreSelected = plan.basePlanId == preSelectedPlanId,
                    onSelectPlan = {
                        handlePlanSelection(context, plan, settingsDataStore)
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transparency footer
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "100% Free Core Invariant",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Core workout recording, map display, sensor telemetry, and offline GPX/TCX/FIT export remain 100% free and unmonetized forever. Subscriptions fund hardware acquisition for upcoming sensors.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
