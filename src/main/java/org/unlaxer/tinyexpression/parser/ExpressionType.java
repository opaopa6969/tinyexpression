package org.unlaxer.tinyexpression.parser;

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
    
    public boolean isBigNumber();
    
    public boolean isPrimitiveNumber();
    public boolean isVoid();
    public boolean isObject();
    public boolean isString();
    public Class<?> javaType();
    public boolean isTimestamp();
    
//    public static Optional<ExpressionType> of(Class<?> clazz);
      
    public Optional<Class<?>> javaTypePrimitive();

  }