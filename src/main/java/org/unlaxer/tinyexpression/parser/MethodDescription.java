package org.unlaxer.tinyexpression.parser;

public class MethodDescription{
	
	public final Class<?> clazz;
	public final Object instance;
	public final String methodName;
	public final MethodDescription.Kind kind;
	public final Object[] parameters;
	
	
	public MethodDescription(Class<?> clazz, String methodName , Object[] parameters) {
		super();
		this.clazz = clazz;
		this.instance = null;
		this.methodName = methodName;
		this.parameters = parameters;
		kind = Kind.javaClassAndMethod;
	}
	
	public MethodDescription(Object instance, String methodName , Object[] parameters) {
		super();
		this.clazz = null;
		this.instance = instance;
		this.methodName = methodName;
		this.parameters = parameters;
		kind = Kind.instanceAndMethod;
	}
	
	public MethodDescription(String methodName , Object[] parameters) {
		super();
		this.clazz = null;
		this.instance = null;
		this.methodName = methodName;
		this.parameters = parameters;
		kind = Kind.methodOnly;
	}

	public enum Kind{
		javaClassAndMethod,
		instanceAndMethod,
		methodOnly,
	}
}