package org.unlaxer.compiler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class MemoryJavaFileObjectForClass extends CustomJavaFileObject{

  byte[] bytes;
  
  protected MemoryJavaFileObjectForClass(ClassName className , byte[] bytes) {
    super(className.fullName(), URI.create("string:///" + className.fullName() + ".class"));
    this.bytes = bytes;
  }
  @Override
  public InputStream openInputStream() throws IOException {
    return new ByteArrayInputStream(bytes);
  }
}