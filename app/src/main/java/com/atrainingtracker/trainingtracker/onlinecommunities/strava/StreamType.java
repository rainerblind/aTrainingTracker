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

package com.atrainingtracker.trainingtracker.onlinecommunities.strava;

import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager.Segments;

/**
 * Created by rainer on 07.09.16.
 */

public enum StreamType {
    SEGMENT(Segments.TABLE_SEGMENT_STREAMS, Segments.SEGMENT_ID, "segments", "latlng, distance, altitude");

    public final String table;
    public final String idName;
    public final String urlPart;
    public final String requestStreamTypes;

    StreamType(String table, String idName, String urlPart, String requestStreamTypes) {
        this.table = table;
        this.idName = idName;
        this.urlPart = urlPart;
        this.requestStreamTypes = requestStreamTypes;
    }

}
