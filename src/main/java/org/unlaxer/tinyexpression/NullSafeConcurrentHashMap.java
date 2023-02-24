package org.unlaxer.tinyexpression;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class NullSafeConcurrentHashMap<K,V> extends ConcurrentHashMap<K, V>{

	private static final long serialVersionUID = 8285812514463071541L;
	
	@SuppressWarnings("unchecked")
	final V NULL_VALUE = (V) new Object();
	@SuppressWarnings("unchecked")
	final K NULL_KEY = (K) new Object();

	public NullSafeConcurrentHashMap() {
		super();
	}

	public NullSafeConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
		super(initialCapacity, loadFactor, concurrencyLevel);
	}

	public NullSafeConcurrentHashMap(int initialCapacity, float loadFactor) {
		super(initialCapacity, loadFactor);
	}

	public NullSafeConcurrentHashMap(int initialCapacity) {
		super(initialCapacity);
	}

	public NullSafeConcurrentHashMap(Map<? extends K, ? extends V> m) {
		super(m);
	}

	@Override
	public V put(K key, V value) {
		key = safeKey(key);
		value = safeValue(value);
		return getValue(super.put(key, value));
	}
	
	@Override
	public V putIfAbsent(K key, V value) {
		key = safeKey(key);
		value = safeValue(value);
		return super.putIfAbsent(key, value);
	}
	
	

	@Override
	public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
		key = safeKey(key);
		return getValue(super.computeIfAbsent(key, mappingFunction));
	}

	@Override
	public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		key = safeKey(key);
		return getValue(super.computeIfPresent(key, remappingFunction));
	}

	@Override
	public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		key = safeKey(key);
		return getValue(super.compute(key, remappingFunction));
	}
	
	@Override
	public V remove(Object key) {
		key = key == null ? NULL_KEY : key;
		return getValue(super.remove(key));
	}

	@Override
	public boolean remove(Object key, Object value) {
		key = key == null ? NULL_KEY : key;
		value = value == null ? NULL_VALUE : value;
		return super.remove(key, value);
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		key = safeKey(key);
		oldValue = safeValue(oldValue);
		newValue = safeValue(newValue);
		return super.replace(key, oldValue, newValue);
	}

	@Override
	public V replace(K key, V value) {
		key = safeKey(key);
		value = safeValue(value);
		return getValue(super.replace(key, value));
	}

	@Override
	public V getOrDefault(Object key, V defaultValue) {
		key = key == null ? NULL_KEY : key;
		defaultValue = safeValue(defaultValue);
		return getValue(super.getOrDefault(key, defaultValue));
	}


	@Override
	public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		key = safeKey(key);
		value = safeValue(value);
		return getValue(super.merge(key, value, remappingFunction));
	}

	@Override
	public boolean containsKey(Object key) {
		key = key == null ? NULL_KEY : key;
		return super.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		value = value == null ? NULL_VALUE : value;
		return super.containsValue(value);
	}

	@Override
	public V get(Object key) {
		key = key == null ? NULL_KEY : key;
		return getValue(super.get(key));
	}

	K safeKey(K key) {
		return key == null ? NULL_KEY : key;
	}
	
	V safeValue(V value) {
		return value == null ? NULL_VALUE : value;
	}
	
	V getValue(V value) {
		return value == NULL_VALUE ? 
				null : value;
	}
	K getKey(K key) {
		return key == NULL_KEY ? 
				null : key;
	}
	
}
