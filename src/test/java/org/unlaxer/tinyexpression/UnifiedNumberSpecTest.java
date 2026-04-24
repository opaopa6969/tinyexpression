package org.unlaxer.tinyexpression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.Test;
import org.unlaxer.tinyexpression.UnifiedNumber.Kind;

/**
 * Spec coverage for UnifiedNumber: all numeric types, kind, and value accessors.
 */
public class UnifiedNumberSpecTest {

    @Test
    public void byte_kindAndValues() {
        UnifiedNumber n = new UnifiedNumber((byte) 42);
        assertEquals(Kind._byte, n.kind);
        assertEquals(42, n.intValue());
        assertEquals(42L, n.longValue());
        assertEquals(42f, n.floatValue(), 0.001f);
        assertEquals(42.0, n.doubleValue(), 0.001);
        assertSame(n.number(), n.number());
    }

    @Test
    public void short_kindAndValues() {
        UnifiedNumber n = new UnifiedNumber((short) 1000);
        assertEquals(Kind._short, n.kind);
        assertEquals(1000, n.intValue());
    }

    @Test
    public void int_kindAndValues() {
        UnifiedNumber n = new UnifiedNumber(Integer.MAX_VALUE);
        assertEquals(Kind._int, n.kind);
        assertEquals(Integer.MAX_VALUE, n.intValue());
        assertEquals((long) Integer.MAX_VALUE, n.longValue());
    }

    @Test
    public void long_kindAndValues() {
        UnifiedNumber n = new UnifiedNumber(Long.MAX_VALUE);
        assertEquals(Kind._long, n.kind);
        assertEquals(Long.MAX_VALUE, n.longValue());
    }

    @Test
    public void float_kindAndValues() {
        UnifiedNumber n = new UnifiedNumber(3.14f);
        assertEquals(Kind._float, n.kind);
        assertEquals(3.14f, n.floatValue(), 0.001f);
    }

    @Test
    public void double_kindAndValues() {
        UnifiedNumber n = new UnifiedNumber(2.718281828);
        assertEquals(Kind._double, n.kind);
        assertEquals(2.718281828, n.doubleValue(), 0.0001);
    }

    @Test
    public void bigInteger_kindAndValues() {
        BigInteger big = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        UnifiedNumber n = new UnifiedNumber(big);
        assertEquals(Kind._bigInteger, n.kind);
        assertEquals(big, n.bigIntegerValue());
    }

    @Test
    public void bigDecimal_kindAndValues() {
        BigDecimal dec = new BigDecimal("123456789.987654321");
        UnifiedNumber n = new UnifiedNumber(dec);
        assertEquals(Kind._bigDecimal, n.kind);
        assertEquals(dec, n.bigDecimalValue());
    }

    @Test
    public void bigIntegerValue_fromNonBigIntegerFallsBackToLong() {
        UnifiedNumber n = new UnifiedNumber(42);
        assertEquals(BigInteger.valueOf(42), n.bigIntegerValue());
    }

    @Test
    public void bigDecimalValue_fromNonBigDecimalFallsBackToDouble() {
        UnifiedNumber n = new UnifiedNumber(1.5f);
        // BigDecimal.valueOf(1.5) should equal 1.5
        assertEquals(0, new BigDecimal("1.5").compareTo(n.bigDecimalValue()));
    }

    @Test
    public void number_returnsWrappedNumber() {
        UnifiedNumber n = new UnifiedNumber(99);
        assertEquals(99, n.number().intValue());
    }
}
