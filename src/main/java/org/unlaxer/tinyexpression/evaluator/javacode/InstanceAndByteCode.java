package org.unlaxer.tinyexpression.evaluator.javacode;

public class InstanceAndByteCode{
  public final Object object;
  public final byte[] bytes;
  public InstanceAndByteCode(Object object, byte[] bytes) {
    super();
    this.object = object;
    this.bytes = bytes;
  }
}