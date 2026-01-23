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

package com.atrainingtracker.trainingtracker.fragments.aftermath;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.exporter.ExportStatus;
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusRepository;
import com.atrainingtracker.trainingtracker.exporter.ExportType;
import com.atrainingtracker.trainingtracker.exporter.FileFormat;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;

import java.util.EnumMap;

// public class ExportStatusDialogFragment extends AppCompatDialogFragment
public class ExportStatusDialogFragment extends DialogFragment {
    public static final String TAG = ExportStatusDialogFragment.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    private static final int PADDING = 2;

    protected long mWorkoutId;

    // TODO: listen to export_status_changed_intent

    @NonNull
    public static ExportStatusDialogFragment newInstance(long workoutId) {
        ExportStatusDialogFragment exportDetailsFragment = new ExportStatusDialogFragment();

        Bundle args = new Bundle();
        args.putLong(WorkoutSummaries.WORKOUT_ID, workoutId);
        exportDetailsFragment.setArguments(args);

        return exportDetailsFragment;
    }


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate");

        mWorkoutId = getArguments().getLong(WorkoutSummaries.WORKOUT_ID);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreateView");

        if (!getShowsDialog()) {
            return createView();
        } else {
            return super.onCreateView(inflater, container, savedInstanceState);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // super.onCreateDialog(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreateDialog");

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setView(createView());
        // TextView title = new TextView(getActivity());
        // title.setBackgroundColor(R.color.my_blue);
        // title.setTextColor(R.color.my_white);
        // title.setText(R.string.export_status);
        // title.setCompoundDrawables(getResources().getDrawable(R.drawable.ic_menu_save), null, null, null);
        // builder.setCustomTitle(title);

        builder.setTitle(R.string.export_status);
        builder.setIcon(R.drawable.ic_save_black_48dp);

        return builder.create();
    }


    @NonNull
    private View createView() {
        if (DEBUG) Log.i(TAG, "createView");

        float scale = getResources().getDisplayMetrics().density;
        int padding_scaled = (int) (PADDING * scale + 0.5f);

        String workoutName = WorkoutSummariesDatabaseManager.getBaseFileName(mWorkoutId);

        ExportStatusRepository repository = ExportStatusRepository.getInstance(getContext());
        EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>> exportStatusTable = repository.getExportStatusMap(workoutName);

        // TODO: set style
        TableLayout tableLayout = new TableLayout(getContext());
        tableLayout.setPadding(padding_scaled, padding_scaled, padding_scaled, padding_scaled);
        // container.addView(tableLayout);
        // setContentView(tableLayout);

        TableRow header = new TableRow(getContext());
        tableLayout.addView(header);
        header.addView(new TextView(getContext()));
        for (ExportType exportType : ExportType.values()) {
            TextView tv = new TextView(getContext());
            tv.setText(exportType.getUiId());
            tv.setPadding(padding_scaled, padding_scaled, padding_scaled, padding_scaled);
            header.addView(tv);
        }

        for (FileFormat fileFormat : FileFormat.values()) {
            TableRow tr = new TableRow(getContext());
            tableLayout.addView(tr);

            TextView tv = new TextView(getContext());
            tv.setText(fileFormat.getUiNameId());
            tv.setPadding(padding_scaled, padding_scaled, padding_scaled, padding_scaled);
            tr.addView(tv);

            for (ExportType exportType : ExportType.values()) {
                ImageView iv = new ImageView(getContext());
                tv.setPadding(padding_scaled, padding_scaled, padding_scaled, padding_scaled);

                ExportStatus exportStatus = exportStatusTable.get(exportType).get(fileFormat);
                switch (exportStatus) {
                    case UNWANTED:
                        iv.setImageResource(R.drawable.ic_not_interested_black_24dp);
                        break;
                    case TRACKING:
                        iv.setImageResource(R.drawable.ic_play_circle_outline_black_24dp);
                        break;
                    case WAITING:
                        iv.setImageResource(R.drawable.ic_hourglass_empty_black_24dp);
                        break;
                    case PROCESSING:
                        iv.setImageResource(R.drawable.ic_cached_black_24dp);
                        break;
                    case FINISHED_SUCCESS:
                        iv.setImageResource(R.drawable.export_success);
                        break;
                    case FINISHED_FAILED:
                        iv.setImageResource(R.drawable.export_failed);
                        break;
                }

                tr.addView(iv);
            }
        }

        if (DEBUG) Log.i(TAG, "returning tableView");
        return tableLayout;
    }

}
