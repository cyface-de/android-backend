package de.cyface.synchronization;

import de.cyface.persistence.MagneticValuePointTable;

final class DirectionsSerializer implements Point3DSerializer {
    @Override
    public String getTableUriPathSegment() {
        return MagneticValuePointTable.URI_PATH;
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
