package de.cyface.persistence.serialization;

import de.cyface.persistence.AccelerationPointTable;

public final class AccelerationsSerializer implements Point3DSerializer {
    @Override
    public String getTableUriPathSegment() {
        return AccelerationPointTable.URI_PATH;
    }

    @Override
    public String getXColumnName() {
        return AccelerationPointTable.COLUMN_AX;
    }

    @Override
    public String getYColumnName() {
        return AccelerationPointTable.COLUMN_AY;
    }

    @Override
    public String getZColumnName() {
        return AccelerationPointTable.COLUMN_AZ;
    }

    @Override
    public String getMeasurementKeyColumnName() {
        return AccelerationPointTable.COLUMN_MEASUREMENT_FK;
    }

    @Override
    public String getTimestampColumnName() {
        return AccelerationPointTable.COLUMN_TIME;
    }
}
