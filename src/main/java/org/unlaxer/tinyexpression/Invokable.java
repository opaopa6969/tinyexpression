package org.unlaxer.tinyexpression;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Invokable<INSTANCE,RETURNING>{
	
	final Method method;
	final INSTANCE instance;
	
	public Invokable(Method method, INSTANCE instance) {
		super();
		this.method = method;
		this.instance = instance;
	}
	public Method method() {
		return method;
	}
	
	public INSTANCE instance() {
		return instance;
	}
	
	@SuppressWarnings("unchecked")
	public RETURNING invoke(Object... parameters) {
		try {
			return (RETURNING) method().invoke(instance(), parameters);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
}