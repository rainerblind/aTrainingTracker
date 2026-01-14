package com.atrainingtracker.trainingtracker.exporter;

/**
 * A simple listener to report progress from a long-running export process
 * to the calling component (e.g., a Worker).
 */
public interface IExportProgressListener {
    void onProgress(int max, int current);
}
