package org.unlaxer.compiler;

import java.util.HashMap;
import java.util.Map;

public class MemoryClassLoader extends ClassLoader {
  private final Map<String, byte[]> bytesByClassName;
  ClassLoader classLoader;
  
  public MemoryClassLoader(ClassLoader parent) {
    this(new HashMap<>(),parent);
  }

  public MemoryClassLoader(Map<String, byte[]> bytesByClassName, ClassLoader parent) {
    super(parent);
    this.bytesByClassName = bytesByClassName;
    classLoader = parent;
  }

  public byte[] getBytes(String name) {
    return bytesByClassName.get(name);
  }

  public Class<?> findClass(String name) throws ClassNotFoundException {
    byte[] bytes = bytesByClassName.get(name);
    if (bytes == null) {
      throw new ClassNotFoundException(name);
    }
    return defineClass(name, bytes, 0, bytes.length);
  }
  
  public byte[] put(String key, byte[] value) {
    return bytesByClassName.put(key, value);
  }

  public void putAll(Map<? extends String, ? extends byte[]> bytesByClassName) {
    this.bytesByClassName.putAll(bytesByClassName);
  }

  ClassLoader classLoader(){
    return classLoader;
  }
}