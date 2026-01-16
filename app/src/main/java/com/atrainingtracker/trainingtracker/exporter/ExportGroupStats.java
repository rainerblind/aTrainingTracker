package com.atrainingtracker.trainingtracker.exporter;

public class ExportGroupStats {
    public final int successCount;
    public final int failureCount;

    public ExportGroupStats(int successCount, int failureCount) {
        this.successCount = successCount;
        this.failureCount = failureCount;
    }

    public int getTotalCount() {
        return successCount + failureCount;
    }
}
