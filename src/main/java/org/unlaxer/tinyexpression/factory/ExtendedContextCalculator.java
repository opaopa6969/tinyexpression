package org.unlaxer.tinyexpression.factory;

public interface ExtendedContextCalculator extends ContextCalculator{
  
  public String formula();
  
  public String javaCode();
  
  public byte[] byteCode();
  
}