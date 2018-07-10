package de.cyface.synchronization;

import android.net.Uri;

import de.cyface.persistence.MeasuringPointsContentProvider;
import de.cyface.persistence.SamplePointTable;

final class AccelerationsSerializer implements Point3DSerializer {
    @Override
    public Uri getTableUri() {
        return MeasuringPointsContentProvider.SAMPLE_POINTS_URI;
    }

    @Override
    public String getXColumnName() {
        return SamplePointTable.COLUMN_AX;
    }

    @Override
    public String getYColumnName() {
        return SamplePointTable.COLUMN_AY;
    }

    @Override
    public String getZColumnName() {
        return SamplePointTable.COLUMN_AZ;
    }

    @Override
    public String getMeasurementKeyColumnName() {
        return SamplePointTable.COLUMN_MEASUREMENT_FK;
    }

    @Override
    public String getTimestampColumnName() {
        return SamplePointTable.COLUMN_TIME;
    }
}
