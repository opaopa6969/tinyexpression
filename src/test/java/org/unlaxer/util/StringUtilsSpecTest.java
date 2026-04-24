package org.unlaxer.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Spec coverage for StringUtils.isBlank / isNoneBlank.
 */
public class StringUtilsSpecTest {

    @Test
    public void isBlank_null() {
        assertTrue(StringUtils.isBlank(null));
    }

    @Test
    public void isBlank_emptyString() {
        assertTrue(StringUtils.isBlank(""));
    }

    @Test
    public void isBlank_whitespaceOnly() {
        assertTrue(StringUtils.isBlank(" "));
        assertTrue(StringUtils.isBlank("  \t\n  "));
    }

    @Test
    public void isBlank_nonBlankString() {
        assertFalse(StringUtils.isBlank("bob"));
        assertFalse(StringUtils.isBlank("  bob  "));
    }

    @Test
    public void isNoneBlank_null() {
        assertFalse(StringUtils.isNoneBlank(null));
    }

    @Test
    public void isNoneBlank_emptyString() {
        assertFalse(StringUtils.isNoneBlank(""));
    }

    @Test
    public void isNoneBlank_whitespaceOnly() {
        assertFalse(StringUtils.isNoneBlank("   "));
    }

    @Test
    public void isNoneBlank_nonBlankString() {
        assertTrue(StringUtils.isNoneBlank("hello"));
        assertTrue(StringUtils.isNoneBlank(" hello "));
    }
}
