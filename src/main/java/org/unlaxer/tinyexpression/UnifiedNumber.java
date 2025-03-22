package org.unlaxer.tinyexpression;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class UnifiedNumber extends Number {

  Number number;
  
  final Kind kind;
  
  public enum Kind{
    _byte(ExpressionTypes._byte),
    _short(ExpressionTypes._short),
    _int(ExpressionTypes._int),
    _long(ExpressionTypes._long),
    _float(ExpressionTypes._float),
    _double(ExpressionTypes._double),
    _bigInteger(ExpressionTypes._bigInteger),
    _bigDecimal(ExpressionTypes._bigDecimal),
    ;
    
    private Kind(ExpressionType expressionType) {
      this.expressionType = expressionType;
    }
    public ExpressionType expressionType;
    
  }

  public UnifiedNumber(Byte byteValue) {
    super();
    kind = Kind._byte;
    number = byteValue;
  }

  public UnifiedNumber(Short shortValue) {
    super();
    kind = Kind._short;
    number = shortValue;
  }

  public UnifiedNumber(Integer intValue) {
    super();
    kind = Kind._int;
    number = intValue;
  }

  public UnifiedNumber(Long longValue) {
    super();
    kind = Kind._long;
    number = longValue;
  }

  public UnifiedNumber(Float floatValue) {
    super();
    kind = Kind._float;
    number = floatValue;
  }

  public UnifiedNumber(Double doubleValue) {
    super();
    kind = Kind._double;
    number = doubleValue;
  }

  public UnifiedNumber(BigInteger bigIntegerValue) {
    super();
    kind = Kind._bigInteger;
    number = bigIntegerValue;
  }

  public UnifiedNumber(BigDecimal bigDecimalValue) {
    super();
    kind = Kind._bigDecimal;
    number = bigDecimalValue;
  }
  
  @Override
  public int intValue() {
    return number.intValue();
  }

  @Override
  public long longValue() {
    return number.longValue();
  }

  @Override
  public float floatValue() {
    return number.floatValue();
  }

  @Override
  public double doubleValue() {
    return number.doubleValue();
  }
  
  public BigInteger bigIntegerValue() {
    return number instanceof BigInteger ? 
        (BigInteger) number : 
        BigInteger.valueOf(number.longValue());
  }
  
  public BigDecimal bigDecimalValue() {
    return number instanceof BigDecimal ? 
        (BigDecimal) number : 
          BigDecimal.valueOf(number.doubleValue());
  }
  
  public Number number() {
    return number;
  }
}
