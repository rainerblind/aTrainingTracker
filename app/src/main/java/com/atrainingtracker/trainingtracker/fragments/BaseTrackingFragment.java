/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2019 Rainer Blind <rainer.blind@gmail.com>
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

package com.atrainingtracker.trainingtracker.fragments;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.trainingtracker.TrainingApplication;

public abstract class BaseTrackingFragment extends Fragment {

    public static final String TAG = ControlTrackingFragment.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG & false;


    protected BANALService.GetBanalServiceInterface mGetBanalServiceIf;


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (DEBUG) Log.d(TAG, "onAttach");

        try {
            mGetBanalServiceIf = (BANALService.GetBanalServiceInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement GetBanalServiceInterface");
        }
    }

    protected void hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View decorView = getActivity().getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);

        // also hide the action bar
        ((AppCompatActivity) getActivity()).getSupportActionBar().hide();
    }

    // Shows the system bars by removing all the flags
    // except for the ones that make the content appear under the system bars.
    protected void showSystemUI() {
        if (getActivity() == null) { return; }

        View decorView = getActivity().getWindow().getDecorView();
        decorView.setSystemUiVisibility(0);

        ((AppCompatActivity) getActivity()).getSupportActionBar().show();
    }

    protected void forceDay() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    protected void forceNight() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }

    protected void followSystem() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.getDefaultNightMode());
    }
}


