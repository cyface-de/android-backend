package de.cyface.synchronization.serialization.proto;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Before;
import org.junit.Test;

public class DeOffsetterTest {

    private DeOffsetter oocut;

    @Before
    public void setUp() {
        oocut = new DeOffsetter();
    }

    @Test
    public void testAbsolute_forFirstNumber() {
        // Arrange

        // Act
        final long result = oocut.absolute(1234567890123L);

        // Assert
        assertThat(result, is(equalTo(1234567890123L)));
    }

    @Test
    public void testAbsolute_forSubsequentNumbers() {
        // Arrange
        final long[] numbers = new long[]{1234567890123L, 100, -100};

        // Act
        final long result1 = oocut.absolute(numbers[0]);
        final long result2 = oocut.absolute(numbers[1]);
        final long result3 = oocut.absolute(numbers[2]);

        // Assert
        final long[] expected = new long[]{1234567890123L, 1234567890223L, 1234567890123L};
        assertThat(result1, is(equalTo(expected[0])));
        assertThat(result2, is(equalTo(expected[1])));
        assertThat(result3, is(equalTo(expected[2])));
    }
}
