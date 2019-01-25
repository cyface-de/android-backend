package de.cyface.synchronization;

import static de.cyface.persistence.Utils.getGeoLocationsUri;
import static de.cyface.persistence.model.MeasurementStatus.FINISHED;
import static de.cyface.persistence.model.MeasurementStatus.OPEN;
import static de.cyface.persistence.model.MeasurementStatus.SYNCED;
import static de.cyface.testutils.SharedTestUtils.insertPoint3d;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.List;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import androidx.annotation.NonNull;
import de.cyface.persistence.DefaultPersistenceBehaviour;
import de.cyface.persistence.GeoLocationsTable;
import de.cyface.persistence.NoSuchMeasurementException;
import de.cyface.persistence.PersistenceLayer;
import de.cyface.persistence.model.GeoLocation;
import de.cyface.persistence.model.Measurement;
import de.cyface.persistence.model.MeasurementStatus;
import de.cyface.persistence.model.PointMetaData;
import de.cyface.persistence.model.Vehicle;
import de.cyface.persistence.serialization.MeasurementSerializer;
import de.cyface.persistence.serialization.Point3dFile;
import de.cyface.testutils.SharedTestUtils;
import de.cyface.utils.CursorIsNullException;

/**
 * Contains utility methods and constants required by the tests within the synchronization project.
 *
 * @author Klemens Muthmann
 * @author Armin Schnabel
 * @version 2.3.0
 * @since 2.1.0
 */
public final class TestUtils {
    /**
     * The tag used to identify Logcat messages from this module.
     */
    final static String TAG = Constants.TAG + ".test";
    /**
     * The content provider authority used during tests. This must be the same as in the manifest and the authenticator
     * configuration.
     */
    final static String AUTHORITY = "de.cyface.synchronization.test.provider";
    /**
     * The account type used during testing. This must be the same as in the authenticator configuration.
     */
    final static String ACCOUNT_TYPE = "de.cyface.synchronization.test";
    /**
     * An username used by the tests to set up a Cyface account for synchronization.
     */
    final static String DEFAULT_USERNAME = "admin";
    /**
     * A password used by the tests to set up a Cyface account for synchronization.
     */
    final static String DEFAULT_PASSWORD = "secret";
    /**
     * Path to an API available for testing.
     */
    @SuppressWarnings("unused") // because this is used in the cyface flavour
    final static String TEST_API_URL = "https://s1.cyface.de:9090/api/v2";
}
