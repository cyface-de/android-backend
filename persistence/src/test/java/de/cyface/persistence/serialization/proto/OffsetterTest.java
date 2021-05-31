package de.cyface.persistence.serialization.proto;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Before;
import org.junit.Test;

public class OffsetterTest {

    @SuppressWarnings("SpellCheckingInspection")
    private Offsetter oocut;

    @Before
    public void setUp() {
        oocut = new Offsetter();
    }

    @Test
    public void testOffset_forFirstNumber() {
        // Arrange

        // Act
        final long result = oocut.offset(1234567890123L);

        // Assert
        assertThat(result, is(equalTo(1234567890123L)));
    }

    @Test
    public void testOffset_forSubsequentNumbers() {
        // Arrange
        final long[] numbers = new long[]{1234567890123L, 1234567890223L, 1234567890123L};

        // Act
        final long result1 = oocut.offset(numbers[0]);
        final long result2 = oocut.offset(numbers[1]);
        final long result3 = oocut.offset(numbers[2]);

        // Assert
        assertThat(result1, is(equalTo(numbers[0])));
        assertThat(result2, is(equalTo(numbers[1] - numbers[0])));
        assertThat(result3, is(equalTo(numbers[2] - numbers[1])));
    }
}
