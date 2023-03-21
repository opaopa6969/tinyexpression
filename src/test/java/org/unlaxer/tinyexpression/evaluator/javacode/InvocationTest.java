package org.unlaxer.tinyexpression.evaluator.javacode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.unlaxer.tinyexpression.CalculationContext;

public class InvocationTest {
  
  public static float foo(CalculationContext calculationContext , String name , int age , boolean isMale) {
    return 0;
  }
  
  public static void main(String[] args) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    
    Method method = InvocationTest.class.getMethod("foo", CalculationContext.class , String.class , int.class , boolean.class);
    method.invoke(method, args);
    
  }

}
