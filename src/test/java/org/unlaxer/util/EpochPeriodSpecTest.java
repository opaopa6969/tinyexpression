package org.unlaxer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * Spec coverage for EpochPeriod: equality, compareTo, toString, instant constructor.
 */
public class EpochPeriodSpecTest {

    @Test
    public void constructor_rangeStoresStartAndEnd() {
        EpochPeriod period = new EpochPeriod(100L, 200L);
        assertEquals(100L, period.startInclusive);
        assertEquals(200L, period.endExclusive);
    }

    @Test
    public void constructor_instantSetsEndToStartPlusOne() {
        EpochPeriod period = new EpochPeriod(500L);
        assertEquals(500L, period.startInclusive);
        assertEquals(501L, period.endExclusive);
    }

    @Test
    public void equals_sameStartAndEndAreEqual() {
        EpochPeriod a = new EpochPeriod(100L, 200L);
        EpochPeriod b = new EpochPeriod(100L, 200L);
        assertEquals(a, b);
    }

    @Test
    public void equals_differentEndNotEqual() {
        EpochPeriod a = new EpochPeriod(100L, 200L);
        EpochPeriod b = new EpochPeriod(100L, 300L);
        assertNotEquals(a, b);
    }

    @Test
    public void equals_differentStartNotEqual() {
        EpochPeriod a = new EpochPeriod(100L, 200L);
        EpochPeriod b = new EpochPeriod(101L, 200L);
        assertNotEquals(a, b);
    }

    @Test
    public void equals_sameInstanceIsEqual() {
        EpochPeriod a = new EpochPeriod(1L, 2L);
        assertEquals(a, a);
    }

    @Test
    public void equals_nullNotEqual() {
        EpochPeriod a = new EpochPeriod(1L, 2L);
        assertFalse(a.equals(null));
    }

    @Test
    public void equals_differentTypeNotEqual() {
        EpochPeriod a = new EpochPeriod(1L, 2L);
        assertFalse(a.equals("not a period"));
    }

    @Test
    public void hashCode_equalObjectsHaveSameHashCode() {
        EpochPeriod a = new EpochPeriod(100L, 200L);
        EpochPeriod b = new EpochPeriod(100L, 200L);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void compareTo_earlierStartComesFirst() {
        EpochPeriod earlier = new EpochPeriod(100L, 200L);
        EpochPeriod later = new EpochPeriod(200L, 300L);
        assertTrue(earlier.compareTo(later) < 0);
        assertTrue(later.compareTo(earlier) > 0);
    }

    @Test
    public void compareTo_sameStartDifferentEndSortsByEnd() {
        EpochPeriod shorter = new EpochPeriod(100L, 150L);
        EpochPeriod longer = new EpochPeriod(100L, 200L);
        assertTrue(shorter.compareTo(longer) < 0);
    }

    @Test
    public void compareTo_equalPeriodsReturnZero() {
        EpochPeriod a = new EpochPeriod(100L, 200L);
        EpochPeriod b = new EpochPeriod(100L, 200L);
        assertEquals(0, a.compareTo(b));
    }

    @Test
    public void sorting_periodsCanBeNaturalySorted() {
        EpochPeriod p1 = new EpochPeriod(300L, 400L);
        EpochPeriod p2 = new EpochPeriod(100L, 200L);
        EpochPeriod p3 = new EpochPeriod(200L, 300L);

        List<EpochPeriod> list = Arrays.asList(p1, p2, p3);
        Collections.sort(list);

        assertEquals(p2, list.get(0)); // 100-200
        assertEquals(p3, list.get(1)); // 200-300
        assertEquals(p1, list.get(2)); // 300-400
    }

    @Test
    public void toString_returnsHyphenedStartEnd() {
        EpochPeriod period = new EpochPeriod(100L, 200L);
        assertEquals("100-200", period.toString());
    }
}
