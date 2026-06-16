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

package com.atrainingtracker.trainingtracker.exporter;

import com.atrainingtracker.R;

public enum ExportStatus {
    UNWANTED(R.string.export_status__unwanted),
    TRACKING(R.string.export_status__tracking),
    TRACKING_FINISHED(R.string.export_status__tracking_finished),
    WAITING(R.string.export_status__waiting),
    PROCESSING(R.string.export_status__processing),
    @Deprecated  // No longer used but might be still in some database.
    FINISHED_RETRY(R.string.export_status__waiting),
    FINISHED_SUCCESS(R.string.export_status__success),
    FINISHED_FAILED(R.string.export_status__failed);

    private final int mUiNameId;

    ExportStatus(int uiNameId) {
        mUiNameId = uiNameId;
    }

    public int getUiNameId() {
        return mUiNameId;
    }
}
