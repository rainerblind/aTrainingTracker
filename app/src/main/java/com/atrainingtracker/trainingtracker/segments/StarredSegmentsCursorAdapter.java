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

package com.atrainingtracker.trainingtracker.segments;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.cursoradapter.widget.CursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter;
import com.atrainingtracker.banalservice.sensor.formater.TimeFormatter;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSegmentsHelper;
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager.Segments;
import com.atrainingtracker.trainingtracker.ui.segments.SimpleSegmentMapViewModel;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.MapView;

import java.text.SimpleDateFormat;
import java.util.Locale;

import kotlinx.coroutines.flow.MutableStateFlow;

/**
 * Created by rainer on 10.08.16.
 */

public class StarredSegmentsCursorAdapter extends CursorAdapter {
    protected static final String[] FROM = {Segments.STRAVA_SEGMENT_ID, Segments.C_ID, Segments.SEGMENT_NAME, Segments.CITY, Segments.COUNTRY, Segments.DISTANCE, Segments.AVERAGE_GRADE, Segments.MAXIMUM_GRADE, Segments.ELEVATION_LOW, Segments.ELEVATION_HIGH, Segments.CLIMB_CATEGORY, Segments.PR_TIME};
    private final String TAG = StarredSegmentsCursorAdapter.class.getSimpleName();
    private final boolean DEBUG = TrainingApplication.getDebug(false);
    protected final Activity mActivity;
    protected final Context mContext;
    // protected static final int[]    TO   = {R.id.tvSegmentName,  R.id.tvSegmentName, R.id.tvSegmentName,    R.id.tvSegmentDistance, R.id.tvSegmentAverageGrade, R.id.tvSegmentClimbCategory, R.id.tvSegmentPRTime, R.id.tvSegmentRank, R.id.tvSegmentPRDate, R.id.tvSegmentLastUpdated};
    @Nullable
    ShowSegmentDetailsInterface mShowSegmentDetailsListener = null;
    final DistanceFormatter distanceFormatter = new DistanceFormatter();
    final TimeFormatter timeFormatter = new TimeFormatter();
    final SimpleDateFormat dateAndTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US); // 2013-03-29T13:49:35Z
    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);               // 2013-03-29
    final StravaSegmentsHelper mStravaSegmentsHelper;
    private boolean isPlayServiceAvailable = true;

    public StarredSegmentsCursorAdapter(Activity activity, Cursor cursor, StravaSegmentsHelper stravaSegmentsHelper, ShowSegmentDetailsInterface showSegmentDetailsInterface) {
        super(activity, cursor, 0);

        mContext = activity;
        mActivity = activity;
        mShowSegmentDetailsListener = showSegmentDetailsInterface;

        mStravaSegmentsHelper = stravaSegmentsHelper;

        isPlayServiceAvailable = checkPlayServices();
    }

    @NonNull
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View row = LayoutInflater.from(context).inflate(R.layout.segment_list_row, parent, false);

        // Use the new Kotlin ViewHolder
        StarredSegmentViewHolder viewHolder = new StarredSegmentViewHolder(
                row,
                mActivity,
                segmentId -> {
                    if (mShowSegmentDetailsListener != null) {
                        mShowSegmentDetailsListener.startSegmentDetailsActivity(segmentId);
                    }
                    return null; // Return for Kotlin Function1 compatibility in Java
                }
        );
        row.setTag(viewHolder);
        return row;
    }

        @Override
    public void bindView(@NonNull View view, @NonNull Context context, @NonNull Cursor cursor) {
        final StarredSegmentViewHolder viewHolder = (StarredSegmentViewHolder) view.getTag();

        final long segmentId = cursor.getLong(cursor.getColumnIndex(Segments.STRAVA_SEGMENT_ID));

        viewHolder.getTvName().setText(cursor.getString(cursor.getColumnIndex(Segments.SEGMENT_NAME)));

        int prTimeInSeconds = cursor.getInt(cursor.getColumnIndex(Segments.PR_TIME));
        if (prTimeInSeconds > 0) {
            viewHolder.getLayoutPr().setVisibility(View.VISIBLE);
            viewHolder.getTvPrTime().setText(timeFormatter.format(prTimeInSeconds)); // Use a formatting helper
        } else {
            viewHolder.getLayoutPr().setVisibility(View.GONE);
        }

        // Set the city text
        String city = cursor.getString(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.CITY));
        if (city != null && !city.isEmpty()) {
            viewHolder.getTvCity().setText(city);
            viewHolder.getTvCity().setVisibility(View.VISIBLE);
        } else {
            // Hide the view if there is no city data to avoid an empty space
           viewHolder.getTvCity().setVisibility(View.GONE);
        }

        viewHolder.getTvDistance().setText(distanceFormatter.format_with_units(cursor.getDouble(cursor.getColumnIndex(Segments.DISTANCE))));

        // Prepend the Unicode symbol for average (Ø) to the text.
        String avgGradeText = String.format(Locale.getDefault(), "\u00D8 %.1f%%", cursor.getDouble(cursor.getColumnIndex(Segments.AVERAGE_GRADE)));
        viewHolder.getTvAverageGrade().setText(avgGradeText);

        float maxGrade = cursor.getFloat(cursor.getColumnIndexOrThrow(SegmentsDatabaseManager.Segments.MAXIMUM_GRADE));
        viewHolder.getTvMaxGrade().setText(String.format(Locale.US, "%.1f%% Max", maxGrade));

        int climbCategory = cursor.getInt(cursor.getColumnIndex(Segments.CLIMB_CATEGORY));
        if (climbCategory > 0) {
            viewHolder.getTvClimbCategory().setText(StravaHelper.translateClimbCategory(climbCategory));
            viewHolder.getTvClimbCategory().setVisibility(View.VISIBLE);
        } else {
            // Hide the chip if the category is 0 or less (not available)
            viewHolder.getTvClimbCategory().setVisibility(View.GONE);
        }

        // Calculate and set Elevation Gain
        double elevHigh = cursor.getDouble(cursor.getColumnIndex(Segments.ELEVATION_HIGH));
        double elevLow = cursor.getDouble(cursor.getColumnIndex(Segments.ELEVATION_LOW));
        long elevationGain = Math.round(elevHigh - elevLow);
        viewHolder.getTvElevationGain().setText(String.format(Locale.getDefault(), "%d m", elevationGain));
        viewHolder.getTvElevationMin().setText(String.format(Locale.getDefault(), "%d m", Math.round(elevLow)));
        viewHolder.getTvElevationMax().setText(String.format(Locale.getDefault(), "%d m", Math.round(elevHigh)));

        if (isPlayServiceAvailable) {
            viewHolder.getViewModel().loadSegment(segmentId, false);
        } else {
            viewHolder.getMapComposeView().setVisibility(View.GONE);
        }

        viewHolder.getRowView().setOnClickListener(v -> {
            mShowSegmentDetailsListener.startSegmentDetailsActivity(segmentId);
        });
    }

    /**
     * Helper method to format seconds into a time string (e.g., HH:MM:SS or MM:SS).
     * @param totalSeconds The total time in seconds.
     * @return A formatted string.
     */
    private String formatSeconds(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
        }
    }


    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        return (apiAvailability.isGooglePlayServicesAvailable(mContext) == ConnectionResult.SUCCESS);
    }


    public interface ShowSegmentDetailsInterface {
        void startSegmentDetailsActivity(long segmentId);
    }
}