package com.atrainingtracker.trainingtracker.Exporter;

import android.content.Context;

public class NotYetImplementedExporter extends BaseExporter {

    public NotYetImplementedExporter(Context context) {
        super(context);
    }

    @Override
    protected ExportResult doExport(ExportInfo exportInfo) {
        return new ExportResult(false, "not yet implemented");
    }

    @Override
    protected Action getAction() {
        return Action.EXPORT;
    }

}
