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
 */

package com.atrainingtracker.trainingtracker.fragments.preferences

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaAuthViewModel
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaAuthState
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaDeauthorizationThread
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaEquipmentSynchronizeThread
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme

class StravaUploadFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var mUpdateStravaEquipment: Preference? = null
    private var mSharedPreferences: SharedPreferences? = null
    private var mHeaderComposeView: ComposeView? = null

    private val authViewModel: StravaAuthViewModel by viewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (DEBUG) Log.i(TAG, "onCreatePreferences(savedInstanceState, rootKey=$rootKey)")

        setPreferencesFromResource(R.xml.prefs, rootKey)

        mUpdateStravaEquipment = findPreference(TrainingApplication.UPDATE_STRAVA_EQUIPMENT)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val prefView = super.onCreateView(inflater, container, savedInstanceState)
        
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        mHeaderComposeView = ComposeView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            updateHeaderContent()
        }

        root.addView(mHeaderComposeView)
        prefView?.let { root.addView(it) }

        return root
    }

    private fun updateHeaderContent() {
        mHeaderComposeView?.setContent {
            ATrainingTrackerTheme {
                val isConnected = TrainingApplication.getStravaAccessToken() != null
                val authState by authViewModel.authState.collectAsStateWithLifecycle()

                LaunchedEffect(authState) {
                    if (authState is StravaAuthState.Success) {
                        // side effects already handled in repo for basic storage, 
                        // but we need to trigger sync and navigation updates
                        updateSelectiveUploadVisibility()
                        StravaEquipmentSynchronizeThread(requireActivity()).start()

                        val repository = SegmentsRepository.getInstance(requireContext())
                        repository.syncSegmentsAsync(BSportType.BIKE)
                        repository.syncSegmentsAsync(BSportType.RUN)
                        
                        authViewModel.resetState()
                    }
                }

                StravaConnectionHeader(
                    modifier = Modifier.statusBarsPadding(),
                    isConnected = isConnected,
                    isConnecting = authState is StravaAuthState.Loading,
                    onConnectClick = {
                        StravaHelper.requestAccessToken(requireContext())
                    },
                    onDisconnectClick = {
                        TrainingApplication.deleteStravaToken()
                        StravaDeauthorizationThread(requireActivity()).start()
                        updateSelectiveUploadVisibility()
                        // Force recompose since we use static call to TrainingApplication
                        updateHeaderContent()
                    }
                )
            }
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView?.let { listView ->
            listView.clipToPadding = false
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
                val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                v.setPadding(0, systemBars.top, 0, systemBars.bottom)
                insets
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (DEBUG) Log.i(TAG, "onResume()")

        mUpdateStravaEquipment?.apply {
            summary = TrainingApplication.getLastUpdateTimeOfStravaEquipment()
            setOnPreferenceClickListener {
                if (DEBUG) Log.d(TAG, "updateStravaEquipment has been clicked")
                StravaEquipmentSynchronizeThread(requireActivity()).start()
                false
            }
        }

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        mSharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        
        updateSelectiveUploadVisibility()
        updateHeaderContent()
    }

    override fun onPause() {
        super.onPause()
        mSharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun updateSelectiveUploadVisibility() {
        val isConnected = TrainingApplication.getStravaAccessToken() != null
        
        findPreference<Preference>(TrainingApplication.UPDATE_STRAVA_EQUIPMENT)?.isVisible = isConnected
        findPreference<Preference>("strava_selective_upload_category")?.isVisible = isConnected
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (DEBUG) Log.i(TAG, "onSharedPreferenceChanged: key=$key")

        if (TrainingApplication.SP_LAST_UPDATE_TIME_OF_STRAVA_EQUIPMENT == key) {
            mUpdateStravaEquipment?.summary = TrainingApplication.getLastUpdateTimeOfStravaEquipment()
        }

        if (TrainingApplication.SP_STRAVA_TOKEN == key) {
            updateSelectiveUploadVisibility()
            updateHeaderContent()
        }
    }

    companion object {
        private val TAG = StravaUploadFragment::class.java.name
        private val DEBUG = TrainingApplication.getDebug(true)
    }
}
