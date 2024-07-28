package org.unlaxer.tinyexpression.evaluator.javacode;

public class ClassNameAndByteCode{
  public final String className;
  public final byte[] byteCode;
  public ClassNameAndByteCode(String className, byte[] byteCode) {
    super();
    this.className = className;
    this.byteCode = byteCode;
  }
}