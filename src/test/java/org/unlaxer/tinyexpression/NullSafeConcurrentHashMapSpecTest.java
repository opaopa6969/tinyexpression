package org.unlaxer.tinyexpression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Spec coverage for NullSafeConcurrentHashMap:
 * get/put/containsKey/containsValue with null keys and null values.
 */
public class NullSafeConcurrentHashMapSpecTest {

    @Test
    public void put_andGet_withNonNullKeyAndValue() {
        NullSafeConcurrentHashMap<String, String> map = new NullSafeConcurrentHashMap<>();
        map.put("key", "value");
        assertEquals("value", map.get("key"));
    }

    @Test
    public void put_andGet_withNullKey() {
        NullSafeConcurrentHashMap<String, String> map = new NullSafeConcurrentHashMap<>();
        map.put(null, "nullKeyValue");
        assertEquals("nullKeyValue", map.get(null));
    }

    @Test
    public void put_andGet_withNullValue() {
        NullSafeConcurrentHashMap<String, String> map = new NullSafeConcurrentHashMap<>();
        map.put("key", null);
        assertNull(map.get("key"));
    }

    @Test
    public void put_andGet_withNullKeyAndNullValue() {
        NullSafeConcurrentHashMap<String, String> map = new NullSafeConcurrentHashMap<>();
        map.put(null, null);
        assertNull(map.get(null));
    }

    @Test
    public void containsKey_nullKey() {
        NullSafeConcurrentHashMap<String, String> map = new NullSafeConcurrentHashMap<>();
        map.put(null, "v");
        assertTrue(map.containsKey(null));
        assertFalse(map.containsKey("missing"));
    }

    @Test
    public void containsValue_nullValue() {
        NullSafeConcurrentHashMap<String, String> map = new NullSafeConcurrentHashMap<>();
        map.put("k", null);
        assertTrue(map.containsValue(null));
        assertFalse(map.containsValue("notPresent"));
    }

    @Test
    public void getOrDefault_nullKeyWithDefault() {
        NullSafeConcurrentHashMap<String, String> map = new NullSafeConcurrentHashMap<>();
        assertEquals("default", map.getOrDefault(null, "default"));
        map.put(null, "found");
        assertEquals("found", map.getOrDefault(null, "default"));
    }

    @Test
    public void putIfAbsent_doesNotOverwriteExistingNonNull() {
        NullSafeConcurrentHashMap<String, String> map = new NullSafeConcurrentHashMap<>();
        map.put("k", "first");
        map.putIfAbsent("k", "second");
        assertEquals("first", map.get("k"));
    }

    @Test
    public void remove_withNullKey() {
        NullSafeConcurrentHashMap<String, String> map = new NullSafeConcurrentHashMap<>();
        map.put(null, "v");
        map.remove(null);
        assertFalse(map.containsKey(null));
    }

    @Test
    public void remove_keyValuePair_withNullKeyAndValue() {
        NullSafeConcurrentHashMap<String, String> map = new NullSafeConcurrentHashMap<>();
        map.put(null, null);
        assertTrue(map.remove(null, null));
        assertFalse(map.containsKey(null));
    }

    @Test
    public void replace_withNullKeyAndValues() {
        NullSafeConcurrentHashMap<String, String> map = new NullSafeConcurrentHashMap<>();
        map.put(null, null);
        boolean replaced = map.replace(null, null, "newValue");
        assertTrue(replaced);
        assertEquals("newValue", map.get(null));
    }

    @Test
    public void computeIfAbsent_withNonNullKey() {
        // computeIfAbsent with a non-null key should behave normally
        NullSafeConcurrentHashMap<String, String> map = new NullSafeConcurrentHashMap<>();
        String result = map.computeIfAbsent("key", k -> "computed_" + k);
        assertEquals("computed_key", result);
        assertEquals("computed_key", map.get("key"));
    }
}
