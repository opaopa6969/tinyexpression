package org.unlaxer.tinyexpression.instances;

public interface TenantID{
  
  int asNumber();
  public default String asString() {
    return String.valueOf(asNumber());
  }
  
  public static TenantID create(int number) {
    return new TenantID() {
      @Override
      public int asNumber() {
        return number;
      }
    };
  }
}