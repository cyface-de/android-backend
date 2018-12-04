package de.cyface.persistence.model;

import de.cyface.persistence.model.Point3DSerializer;
import de.cyface.persistence.RotationPointTable;

public final class RotationsSerializer implements Point3DSerializer {
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
