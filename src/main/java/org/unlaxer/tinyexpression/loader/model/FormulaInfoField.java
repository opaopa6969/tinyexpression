package org.unlaxer.tinyexpression.loader.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FormulaInfoField{
 
  Class<? extends Function<Object,String>> converter() default StringToString.class;
  
  public static class StringToString implements Function<Object,String>{

    @Override
    public String apply(Object t) {
      return (String)t;
    }
  }
  
  public static class StringsToString implements Function<Object,String>{

    @SuppressWarnings("unchecked")
    @Override
    public String apply(Object strings) {
      return ((Collection<String>)strings).stream().collect(Collectors.joining(","));
    }
    
  }
}