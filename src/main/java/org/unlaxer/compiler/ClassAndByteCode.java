package org.unlaxer.compiler;

public class ClassAndByteCode{
  public final Class<?> clazz;
  public final byte[] bytes;
  public ClassAndByteCode(Class<?> clazz, byte[] bytes) {
    super();
    this.clazz = clazz;
    this.bytes = bytes;
  }
}