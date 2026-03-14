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

package com.atrainingtracker.banalservice;


import android.content.Context;
import com.atrainingtracker.R;

public enum BSportType {
    UNKNOWN(R.drawable.bsport_other, R.string.sport_type_other),
    RUN(R.drawable.bsport_run, R.string.sport_type_run),
    BIKE(R.drawable.bsport_bike, R.string.sport_type_bike),
    CONFLICT(R.drawable.bsport_other, R.string.sport_type_other);

    private final int iconResId;
    private final int stringResId;

    BSportType(int iconResId, int stringResId) {
        this.iconResId = iconResId;
        this.stringResId = stringResId;
    }

    public int getIconResId() {
        return iconResId;
    }

    public int getStringResId() {
        return stringResId;
    }

    public String getName(Context context) {
        return context.getString(stringResId);
    }

    public BSportType or(BSportType other) {
        return BSportType.values()[(ordinal() | other.ordinal())];
    }

}
