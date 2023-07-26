package org.unlaxer.tinyexpression.factory;

@Deprecated
public interface ExtendedContextCalculator extends ContextCalculator{
  
  public String formula();
  
  public String javaCode();
  
  public byte[] byteCode();
  
}