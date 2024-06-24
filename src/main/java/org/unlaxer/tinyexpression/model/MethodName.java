package org.unlaxer.tinyexpression.model;

import org.unlaxer.Specifier;
import org.unlaxer.util.FactoryBoundCache;

public class MethodName extends Specifier<MethodName>{


	public MethodName(Class<?> specifiedClass, Enum<?> subName) {
		super(specifiedClass, subName);
	}

	public MethodName(Class<?> specifiedClass, String subName) {
		super(specifiedClass, subName);
	}

	public MethodName(Class<?> specifiedClass) {
		super(specifiedClass);
	}

	public MethodName(Enum<?> enumName) {
		super(enumName);
	}

	public MethodName(String stringName) {
		super(stringName);
	}
	
	public static MethodName of(String name){
		return specifierByString.get(name);
	}
	
	public static MethodName of(Enum<?> name){
		return specifierByEnum.get(name);
	}
	
	public static MethodName of(Class<?> clazz){
		return specifierByClass.get(clazz);
	}
	
	public static MethodName of(Class<?> clazz,Specifier<?> name){
		return specifierByString.get(clazz.getName()+"("+name.toString()+")");
	}
	
	public static MethodName of(Class<?> clazz,String name){
		return specifierByString.get(clazz.getName()+"("+name+")");
	}
	
	public static MethodName classBaseOf(Object object){
		return specifierByString.get(object.getClass().getName());
	}
	
	static FactoryBoundCache<Class<?>,MethodName> specifierByClass = 
			new FactoryBoundCache<>(MethodName::new);
	
	static FactoryBoundCache<String,MethodName> specifierByString = 
			new FactoryBoundCache<>(MethodName::new);
	
	static FactoryBoundCache<Enum<?>,MethodName> specifierByEnum = 
			new FactoryBoundCache<>(MethodName::new);


}