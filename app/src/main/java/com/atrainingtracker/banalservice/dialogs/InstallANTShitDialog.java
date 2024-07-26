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

package com.atrainingtracker.banalservice.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.trainingtracker.TrainingApplication;

/**
 * Created by rainer on 05.01.17.
 */

public class InstallANTShitDialog extends DialogFragment {
    public static final String TAG = InstallANTShitDialog.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    private static final String ANT_PLUGIN_PACKAGE_NAME = "com.dsi.ant.plugins.antplus";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext());

        alertDialogBuilder.setTitle(getString(R.string.ANT_Installation));

        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        // Inflate and set the layout for the dialog
        final View mainView = inflater.inflate(R.layout.check_ant_installation_dialog, null);
        alertDialogBuilder.setView(mainView);

        // check ANT+ Plugin Service
        boolean isANTPluginServiceInstalled = BANALService.isANTPluginServiceInstalled(getContext());
        TextView tv = mainView.findViewById(R.id.tvPluginService);
        tv.setTextColor(getResources().getColor(isANTPluginServiceInstalled ? R.color.dark_green : R.color.dark_red));

        Button button = mainView.findViewById(R.id.bPluginService);
        if (isANTPluginServiceInstalled) {
            button.setClickable(false);
            button.setEnabled(false);
        } else {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    installService(ANT_PLUGIN_PACKAGE_NAME);
                }
            });
        }

        // check ANT Radio Service
        boolean isANTRadioServiceInstalled = BANALService.isANTRadioServiceInstalled();
        tv = mainView.findViewById(R.id.tvRadioService);
        tv.setTextColor(getResources().getColor(isANTRadioServiceInstalled ? R.color.dark_green : R.color.dark_red));

        button = mainView.findViewById(R.id.bRadioService);
        if (isANTRadioServiceInstalled) {
            button.setClickable(false);
            button.setEnabled(false);
        } else {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    installService(BANALService.URI_ANT_RADIO_SERVICE);
                }
            });
        }

        // check ANT USB Service
        if (!BANALService.hasUsbHostFeature(getContext())) {
            LinearLayout ll = mainView.findViewById(R.id.llUSBService);
            ll.setVisibility(View.GONE);
        } else {
            boolean isANTUSBServiceInstalled = BANALService.isANTUSBServiceInstalled();
            tv = mainView.findViewById(R.id.tvUSBService);
            tv.setTextColor(getResources().getColor(isANTUSBServiceInstalled ? R.color.dark_green : R.color.dark_red));

            button = mainView.findViewById(R.id.bUSBService);
            if (isANTUSBServiceInstalled) {
                button.setClickable(false);
                button.setEnabled(false);
            } else {
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        installService(BANALService.URI_ANT_USB_SERVICE);
                    }
                });
            }
        }

        CheckBox cb = mainView.findViewById(R.id.cbDoNotAskAgain);
        cb.setChecked(!TrainingApplication.checkANTInstallation());
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                TrainingApplication.setCheckANTInstallation(!isChecked);
            }
        });


        alertDialogBuilder.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });

        return alertDialogBuilder.create();
    }

    private void installService(String uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + uri)));
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + uri)));
        }
    }
}
