package org.unlaxer.tinyexpression;

import java.math.RoundingMode;
import java.util.Optional;

public interface CalculationContext {
	
	public enum Angle{
		RADIAN,DEGREE
	}

  default void set(Enum<?> name, String value) {
    set(name.name(),value);
  }

	void set(String name, String value);


	default Optional<String> getString(Enum<?>name){
	  return getString(name.name());
	}

	Optional<String> getString(String name);
	
	default void set(Enum<?> name, float value) {
	  set(name.name(),value);
	}

	void set(String name, float value);
	

  default Optional<Float> getValue(Enum<?> name){
    return getValue(name.name());
  }

	Optional<Float> getValue(String name);
	
	default void set(Enum<?> name, boolean value) {
	  set(name.name(),value);
	}

	void set(String name, boolean value);
	
	default Optional<Boolean> getBoolean(Enum<?> name){
	  return getBoolean(name.name());
	}

	Optional<Boolean> getBoolean(String name);
	
	default <T> Optional<T> getObject(Enum<?> name , Class<T> clazz){
	  return getObject(name.name(),clazz);
	}

	<T> Optional<T> getObject(String name , Class<T> clazz);

	default void setObject(Enum<?> name , Object object) {
	  setObject(name.name() ,object);
	}

	void setObject(String name , Object object);

	default <T> Optional<T> getObject(Class<T> clazz){
		return getObject(clazz.getName() ,clazz);
	}
	
	default void set(Object object) {
		setObject(object.getClass().getName(), object);
	}
	
  default boolean isExists(Enum<?> name) {
    return isExists(name.name());
  }
	
	boolean isExists(String name);

	double radianAngle(double angleValue);

	float nextRandom();
	
	public static CalculationContext newContext(int scale, RoundingMode roundingMode , Angle angle) {
		return new NormalCalculationContext(scale , roundingMode , angle);
	}
	
	public static CalculationContext newContext() {
		return new NormalCalculationContext();
	}
	
	public static CalculationContext newConcurrentContext(int scale, RoundingMode roundingMode , Angle angle) {
		return new ConcurrentCalculationContext(scale , roundingMode , angle);
	}
	
	public static CalculationContext newConcurrentContext() {
		return new ConcurrentCalculationContext();
	}
	
	public Angle angle();
	
	public int scale();
	
	public RoundingMode roundingMode();
	
	public default Object getFromNumberOrStringOrBoolean(String name) {
    Optional<Float> value = getValue(name);
    if(value.isPresent()){
      return value.get();
    }
    Optional<String> string = getString(name);
    if(string.isPresent()) {
      return string.get();
    }
    Optional<Boolean> boolean1 = getBoolean(name);
    if(boolean1.isPresent()) {
      return boolean1.get();
    }
    return null;
	}
}
