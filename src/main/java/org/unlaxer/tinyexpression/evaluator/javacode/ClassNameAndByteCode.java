package org.unlaxer.tinyexpression.evaluator.javacode;

public interface ClassNameAndByteCode{
  public String className();
  public byte[] byteCode();
  
  public static ClassNameAndByteCode of(String className , byte[] byteCode) {
    return new ClassNameAndByteCode() {
      public String className() {return className;}
      public byte[] byteCode() {return byteCode;}
    };
  }
}