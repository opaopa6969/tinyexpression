package org.unlaxer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.Test;

/**
 * Spec coverage for MultiDateParser.toEpochMilli and Kind.format.
 */
public class MultiDateParserSpecTest {

    // Unix epoch in JST (+09:00): 1970-01-01 09:00:00 → epoch millis 0
    private static final long EPOCH_JST_MILLIS = 0L;

    @Test
    public void toEpochMilli_fullFormat() {
        // "yyyy-MM-dd HH:mm:ss" — JST offset (+9) applied
        Optional<Long> result = MultiDateParser.toEpochMilli("1970-01-01 00:00:00", Optional.empty());
        assertTrue(result.isPresent());
        // 00:00:00 JST = -9 hours from UTC = -32400000 ms
        assertEquals(-32400000L, result.get().longValue());
    }

    @Test
    public void toEpochMilli_fullWithUnderscore() {
        Optional<Long> result = MultiDateParser.toEpochMilli("1970-01-01_00:00:00", Optional.empty());
        assertTrue(result.isPresent());
        assertEquals(-32400000L, result.get().longValue());
    }

    @Test
    public void toEpochMilli_fullWithSlash() {
        Optional<Long> result = MultiDateParser.toEpochMilli("1970/01/01 00:00:00", Optional.empty());
        assertTrue(result.isPresent());
        assertEquals(-32400000L, result.get().longValue());
    }

    @Test
    public void toEpochMilli_dateOnly() {
        // "yyyy-MM-dd" → start of day
        Optional<Long> result = MultiDateParser.toEpochMilli("1970-01-01", Optional.empty());
        assertTrue(result.isPresent());
        assertEquals(-32400000L, result.get().longValue());
    }

    @Test
    public void toEpochMilli_epochMillisAsString() {
        Optional<Long> result = MultiDateParser.toEpochMilli("0", Optional.empty());
        assertTrue(result.isPresent());
        assertEquals(0L, result.get().longValue());
    }

    @Test
    public void toEpochMilli_isoOffsetDateTime() {
        // ISO_OFFSET_DATE_TIME: 1970-01-01T00:00:00Z = 0ms
        Optional<Long> result = MultiDateParser.toEpochMilli("1970-01-01T00:00:00Z", Optional.empty());
        assertTrue(result.isPresent());
        assertEquals(0L, result.get().longValue());
    }

    @Test
    public void toEpochMilli_invalidReturnsEmpty() {
        Optional<Long> result = MultiDateParser.toEpochMilli("not-a-date", Optional.empty());
        assertFalse(result.isPresent());
    }

    @Test
    public void toEpochMilli_customTimezoneOffset() {
        // UTC+0 means 1970-01-01 00:00:00 → epoch=0
        Optional<Long> result = MultiDateParser.toEpochMilli("1970-01-01 00:00:00", Optional.of(0));
        assertTrue(result.isPresent());
        assertEquals(0L, result.get().longValue());
    }

    @Test
    public void kindFormat_fullFormat() {
        // 0ms epoch in JST = "1970-01-01 09:00:00"
        Optional<String> formatted = MultiDateParser.Kind.full.format(0L);
        assertTrue(formatted.isPresent());
        assertEquals("1970-01-01 09:00:00", formatted.get());
    }

    @Test
    public void kindFormat_dateOnly() {
        Optional<String> formatted = MultiDateParser.Kind.dateOnly.format(0L);
        assertTrue(formatted.isPresent());
        assertEquals("1970-01-01", formatted.get());
    }

    @Test
    public void applyTimezone_zeroOffsetReturnsUtcMillis() {
        long millis = MultiDateParser.applyTimezone(
                java.time.LocalDateTime.of(1970, 1, 1, 0, 0, 0),
                Optional.of(0));
        assertEquals(0L, millis);
    }
}
