package com.atrainingtracker.trainingtracker.fragments;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.atrainingtracker.R;

import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter;
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter;
import com.atrainingtracker.trainingtracker.MyHelper;
import com.atrainingtracker.trainingtracker.database.ExtremaType;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;

import java.util.Locale;

/**
 * A ViewHolder that manages the self-contained 'workout_summary_details.xml' layout.
 */
public class WorkoutDetailsViewHolder {

    private final View mView;
    private final Context mContext;

    // Row 1
    private final TextView mTvDistance;
    private final TextView mTvDuration;

    // Row 2
    private final LinearLayout mRow2;
    private final TextView mTvAscent;
    private final TextView mTvDescent;
    private final TextView mTvMinAltitude;
    private final TextView mTvMaxAltitude;

    public WorkoutDetailsViewHolder(@NonNull View view, Context context) {
        mView = view;
        mContext = context;

        // Find all views from the inflated layout
        mTvDistance = view.findViewById(R.id.detail_distance);
        mTvDuration = view.findViewById(R.id.detail_duration);

        mRow2 = view.findViewById(R.id.workout_details_row2);
        mTvAscent = view.findViewById(R.id.detail_ascent);
        mTvDescent = view.findViewById(R.id.detail_descent);
        mTvMinAltitude = view.findViewById(R.id.detail_min_altitude);
        mTvMaxAltitude = view.findViewById(R.id.detail_max_altitude);
    }

    public void bind(@NonNull Cursor cursor, long workoutId) {

        // --- Bind Row 1 (Distance and time) ---
        // distance
        String distance = (new DistanceFormatter()).format(cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m)));
        String unit = mContext.getString(MyHelper.getDistanceUnitNameId());
        mTvDistance.setText(mContext.getString(R.string.value_unit_string_string, distance, unit));

        // active time
        int activeTime = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_ACTIVE_s));
        mTvDuration.setText((new TimeFormatter()).format(activeTime));


        // --- Bind Row 2 (Ascent, Descent, Min/Max Altitude) ---
        boolean hasRow2Data = false;

        // Ascent
        int ascentM = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.ASCENDING));
        if (ascentM > 0) {
            mTvAscent.setText(String.format(Locale.getDefault(), "%d %s", ascentM, 'm'));  // TODO: might be simpified?
            mTvAscent.setVisibility(View.VISIBLE);
            hasRow2Data = true;
        } else {
            mTvAscent.setVisibility(View.GONE);
        }

        // Descent
        int descentM = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.DESCENDING));
        if (descentM > 0) {
            mTvDescent.setText(String.format(Locale.getDefault(), "%d %s", descentM, 'm'));
            mTvDescent.setVisibility(View.VISIBLE);
            hasRow2Data = true;
        } else {
            mTvDescent.setVisibility(View.GONE);
        }

        // Min Altitude
        Double minAlt = WorkoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.ALTITUDE, ExtremaType.MIN);
        if (minAlt != null) {
            mTvMinAltitude.setText(String.format(Locale.getDefault(), "%.0f %s", minAlt, 'm'));
            mTvMinAltitude.setVisibility(View.VISIBLE);
            hasRow2Data = true;
        } else {
            mTvMinAltitude.setVisibility(View.GONE);
        }

        // Max Altitude
        Double maxAlt = WorkoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.ALTITUDE, ExtremaType.MAX);
        if (maxAlt != null) {
            mTvMaxAltitude.setText(String.format(Locale.getDefault(), "%.0f %s", maxAlt, 'm'));
            mTvMaxAltitude.setVisibility(View.VISIBLE);
            hasRow2Data = true;
        } else {
            mTvMaxAltitude.setVisibility(View.GONE);
        }

        // Hide the entire second row if no data exists for it
        mRow2.setVisibility(hasRow2Data ? View.VISIBLE : View.GONE);

        // TODO: Hide the entire container if no data is bound (optional, but good practice)
        mView.setVisibility(View.VISIBLE);
    }
}
