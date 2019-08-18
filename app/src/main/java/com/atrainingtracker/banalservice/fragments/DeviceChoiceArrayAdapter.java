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

package com.atrainingtracker.banalservice.fragments;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.helpers.UIHelper;

import java.util.List;

public class DeviceChoiceArrayAdapter extends ArrayAdapter<DeviceType> {
    Context mContext;
    Protocol mProtocol;

    public DeviceChoiceArrayAdapter(Context context, int resourceId, List<DeviceType> items, Protocol protocol) {
        super(context, resourceId, items);
        mContext = context;
        mProtocol = protocol;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
        DeviceType deviceType = getItem(position);

        LayoutInflater mInflater = (LayoutInflater) mContext.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.device_choice_row, null);
            holder = new ViewHolder();
            holder.txtTitle = convertView.findViewById(R.id.title);
            holder.imageView = convertView.findViewById(R.id.icon);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.txtTitle.setText(UIHelper.getNameId(deviceType));

        // TODO use this (one text view with compound drawable instead of this complex fucking stuff)
        // holder.txtTitle.setCompoundDrawablesWithIntrinsicBounds(antDeviceType.getImageId(), 0, 0, 0);
        // TODO: READ ABOVE COMMENT!
        holder.imageView.setImageResource(UIHelper.getIconId(deviceType, mProtocol));
        return convertView;
    }

    /*private view holder class*/
    private class ViewHolder {
        ImageView imageView;
        TextView txtTitle;
    }
}
