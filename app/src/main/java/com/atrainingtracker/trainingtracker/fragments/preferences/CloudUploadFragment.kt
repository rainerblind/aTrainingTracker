/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.fragments.preferences

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth

class CloudUploadFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var mSharedPreferences: SharedPreferences? = null
    private var mAwaitDropboxResult = false
    private var mHeaderComposeView: ComposeView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mAwaitDropboxResult = savedInstanceState?.getBoolean(KEY_AWAIT_DROPBOX, false) ?: false
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (DEBUG) Log.i(TAG, "onCreatePreferences(savedInstanceState, rootKey=$rootKey)")
        setPreferencesFromResource(R.xml.prefs_dropbox, null)
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
                Column {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Column(
                            modifier = Modifier.statusBarsPadding()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.Dropbox),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Connection Control (Buttons) - Matching Strava pattern
                    val isConnected = TrainingApplication.uploadToDropbox()
                    DropboxConnectionHeader(
                        isConnected = isConnected,
                        onConnectClick = {
                            Auth.startOAuth2PKCE(requireActivity(), BuildConfig.DROPBOX_APP_KEY, DbxRequestConfig(BuildConfig.DROPBOX_APP_KEY))
                            mAwaitDropboxResult = true
                        },
                        onDisconnectClick = {
                            TrainingApplication.deleteDropboxCredential()
                            TrainingApplication.setUploadToDropbox(false)
                            updateHeaderContent()
                        }
                    )
                }
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

        if (mAwaitDropboxResult) {
            val dbxCredential = Auth.getDbxCredential()
            if (dbxCredential != null) {
                TrainingApplication.storeDropboxCredential(dbxCredential)
                TrainingApplication.setUploadToDropbox(true)
            }
            mAwaitDropboxResult = false
        }

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        mSharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        
        updateHeaderContent()
    }

    override fun onPause() {
        super.onPause()
        mSharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_AWAIT_DROPBOX, mAwaitDropboxResult)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (DEBUG) Log.i(TAG, "onSharedPreferenceChanged: key=$key")

        if (TrainingApplication.SP_UPLOAD_TO_DROPBOX == key) {
            updateHeaderContent()
        }
    }

    companion object {
        private val TAG = CloudUploadFragment::class.java.name
        private val DEBUG = TrainingApplication.getDebug(false)
        private const val KEY_AWAIT_DROPBOX = "KEY_AWAIT_DROPBOX"
    }
}
