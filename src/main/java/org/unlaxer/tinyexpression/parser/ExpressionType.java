package org.unlaxer.tinyexpression.parser;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import org.unlaxer.Tag;

public interface ExpressionType{
  public Tag asTag();
  
  public boolean isBoolean();
  public boolean isShort();
  public boolean isByte();
  public boolean isInt();
  public boolean isFloat();
  public boolean isLong();
  public boolean isDouble();
  public boolean isBigInteger();
  public boolean isBigDecimal();
  public boolean isNumber();
  
  public default String javaLiteralSuffix() {
  	return "";
  };
  
  public boolean isBigNumber();
  public default boolean isNotBigNumber() {
    return false == isBigNumber();
  }
  
  public boolean isPrimitiveNumber();
  public boolean isVoid();
  public boolean isObject();
  public boolean isString();
  public boolean isTimestamp();
  public boolean isExternalJavaType();
  
  public Class<?> javaType();
  public default String javaTypeAsString() {
    return javaType().getTypeName();
  }
  
  public Optional<String> lowerCaseTypeName();
  
//    public static Optional<ExpressionType> of(Class<?> clazz);
    
  public Optional<Class<?>> javaTypePrimitive();
  
  public static record PrePost(String pre,String post) {};
  
  public default PrePost wrapNumber() {
    if(isInt()) {
      return new PrePost("((int)" ,")");
    }
    if(isShort()) {
      return new PrePost("((short)" ,")");
    }
    if(isByte()) {
      return new PrePost("((byte)" ,")");
    }
    if(isFloat()) {
      return new PrePost("((float)" ,")");
    }
    if(isLong()) {
      return new PrePost("((long)" ,")");
    }
    if(isDouble()) {
      return new PrePost("((double)" ,")");
    }
    if(isBigInteger()) {
      return new PrePost("BigInteger.valueOf(" ,")");
    }
    if(isBigDecimal()) {
      return new PrePost("BigDecimal.valueOf(" ,")");
    }
    throw new IllegalArgumentException();
  }
  
  public default String zeroNumber() {
    
    if(isInt() || isShort() || isByte()) {
      return "0";
    }
    if(isFloat()) {
      return "0f";
    }
    if(isLong()) {
      return "0L";
    }
    if(isDouble()) {
      return "0d";
    }
    if(isBigInteger()) {
      return "new BigInteger(\"0\")";
    }
    if(isBigDecimal()) {
      return "new BigDecimal(\"0\")";
    }
    throw new IllegalArgumentException();
  }
  
  public default Number parseNumber(String numberToken) {
    
    if(isInt()) {
      return Integer.parseInt(numberToken);
    }
    if(isShort()) {
      return Short.parseShort(numberToken);
    }
    if(isByte()) {
      return Byte.parseByte(numberToken);
    }
    if(isFloat()) {
      return Float.parseFloat(numberToken);
    }
    if(isLong()) {
      return Long.parseLong(numberToken);
    }
    if(isDouble()) {
      return Double.parseDouble(numberToken);
    }
    if(isBigInteger()) {
      return new BigInteger(numberToken);
    }
    if(isBigDecimal()) {
      return new BigDecimal(numberToken);
    }
    throw new IllegalArgumentException();
  }
  
  public default String numberWithSuffix(String numberToken) {
    
    return String.valueOf(parseNumber(numberToken))+javaLiteralSuffix();
  }
  
  public default String valueMethod() {
    if(isInt()) {
      return "intValue()";
    }
    if(isShort()) {
      return "shortValue()";
    }
    if(isByte()) {
      return "byteValue()";
    }
    if(isFloat()) {
      return "floatValue()";
    }
    if(isLong()) {
      return "longValue()";
    }
    if(isDouble()) {
      return "douleValue()";
    }
    if(isBigInteger()) {
      return "";
    }
    if(isBigDecimal()) {
      return "";
    }
    throw new IllegalArgumentException();
  }
  
}