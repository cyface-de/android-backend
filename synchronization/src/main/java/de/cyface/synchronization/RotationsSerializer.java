package de.cyface.synchronization;

import de.cyface.persistence.RotationPointTable;

final class RotationsSerializer implements Point3DSerializer {
    @Override
    public String getTableUriPathSegment() {
        return RotationPointTable.URI_PATH;
    }

    @Override
    public String getXColumnName() {
        return RotationPointTable.COLUMN_RX;
    }

    @Override
    public String getYColumnName() {
        return RotationPointTable.COLUMN_RY;
    }

    @Override
    public String getZColumnName() {
        return RotationPointTable.COLUMN_RZ;
    }

    @Override
    public String getMeasurementKeyColumnName() {
        return RotationPointTable.COLUMN_MEASUREMENT_FK;
    }

    @Override
    public String getTimestampColumnName() {
        return RotationPointTable.COLUMN_TIME;
    }
}
