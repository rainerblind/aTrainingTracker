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


public class DeviceIdAndNameArrayAdapter extends ArrayAdapter<Long> {
    private static final String TAG = "DeviceIdAndNameSpinnerAdapter";

    protected Context mContext;
    protected Long[] mDeviceIdsArray;
    protected String[] mNamesArray;

    public DeviceIdAndNameArrayAdapter(Context context, int textViewResourceId, Long[] deviceIdsArray, String[] namesArray) {
        super(context, textViewResourceId, deviceIdsArray);
        mContext = context;
        mDeviceIdsArray = deviceIdsArray;
        mNamesArray = namesArray;
    }

    // This is for the "passive" state of the spinner
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
        }

        TextView label = convertView.findViewById(android.R.id.text1);
        label.setText(mNamesArray[position]);

        return label;
    }

    // And here is when the "chooser" is popped up
    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
        }

        TextView label = (TextView) convertView;
        label.setText(mNamesArray[position]);

        return label;
    }

}
