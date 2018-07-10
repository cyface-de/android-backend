package de.cyface.synchronization;

import android.net.Uri;

import de.cyface.persistence.MagneticValuePointTable;
import de.cyface.persistence.MeasuringPointsContentProvider;

final class DirectionsSerializer implements Point3DSerializer {
    @Override
    public Uri getTableUri() {
        return MeasuringPointsContentProvider.MAGNETIC_VALUE_POINTS_URI;
    }

    @Override
    public String getXColumnName() {
        return MagneticValuePointTable.COLUMN_MX;
    }

    @Override
    public String getYColumnName() {
        return MagneticValuePointTable.COLUMN_MY;
    }

    @Override
    public String getZColumnName() {
        return MagneticValuePointTable.COLUMN_MZ;
    }

    @Override
    public String getMeasurementKeyColumnName() {
        return MagneticValuePointTable.COLUMN_MEASUREMENT_FK;
    }

    @Override
    public String getTimestampColumnName() {
        return MagneticValuePointTable.COLUMN_TIME;
    }
}
