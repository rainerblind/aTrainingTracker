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

package com.atrainingtracker.trainingtracker.views;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.banalservice.sensor.SensorType;

// TODO: show an image that represents the SensorType
// TODO: it would be awesome to show whether or not this sensor is currently available/active and its current value
public class SensorArrayAdapter extends ArrayAdapter<SensorType> {
    private static final String TAG = "SensorSpinnerAdapter";

    @NonNull
    protected final Context mContext;
    @NonNull
    protected final SensorType[] mSensorTypeArray;

    public SensorArrayAdapter(@NonNull Context context, int textViewResourceId, @NonNull SensorType[] sensorTypeArray) {
        super(context, textViewResourceId, sensorTypeArray);
        mContext = context;
        mSensorTypeArray = sensorTypeArray;
    }

    // This is for the "passive" state of the spinner
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
        }

        // android.R.id.text1 is default text view in resource of the android.
        // android.R.layout.simple_spinner_item is default layout in resources of android.

        TextView label = convertView.findViewById(android.R.id.text1);
        // label.setTextColor(Color.BLACK);
        label.setText(mSensorTypeArray[position].getFullNameId());

        return label;
    }

    // And here is when the "chooser" is popped up
    @NonNull
    @Override
    public View getDropDownView(int position, @Nullable View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
        }

        TextView label = (TextView) convertView;
        // label.setTextColor(Color.BLACK);
        label.setText(mSensorTypeArray[position].getFullNameId());

        return label;
    }

}
