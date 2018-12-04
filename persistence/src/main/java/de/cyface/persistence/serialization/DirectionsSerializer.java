package de.cyface.persistence.serialization;

import de.cyface.persistence.DirectionPointTable;

public final class DirectionsSerializer implements Point3DSerializer {
    @Override
    public String getTableUriPathSegment() {
        return DirectionPointTable.URI_PATH;
    }

    @Override
    public String getXColumnName() {
        return DirectionPointTable.COLUMN_MX;
    }

    @Override
    public String getYColumnName() {
        return DirectionPointTable.COLUMN_MY;
    }

    @Override
    public String getZColumnName() {
        return DirectionPointTable.COLUMN_MZ;
    }

    @Override
    public String getMeasurementKeyColumnName() {
        return DirectionPointTable.COLUMN_MEASUREMENT_FK;
    }

    @Override
    public String getTimestampColumnName() {
        return DirectionPointTable.COLUMN_TIME;
    }
}
