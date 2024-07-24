package org.unlaxer.tinyexpression.instances;

public interface TenantID{
  
  int asNumber();
  public default String asString() {
    return String.valueOf(asNumber());
  }
}