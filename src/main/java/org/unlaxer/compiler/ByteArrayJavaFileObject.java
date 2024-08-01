package org.unlaxer.compiler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import javax.tools.SimpleJavaFileObject;

class ByteArrayJavaFileObject extends SimpleJavaFileObject {
  private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

  protected ByteArrayJavaFileObject(String name, Kind kind) {
    super(URI.create("string:///" + name.replace('.', '/') + kind.extension), kind);
  }

  @Override
  public OutputStream openOutputStream() throws IOException {
    return outputStream;
  }

  public byte[] getBytes() {
    return outputStream.toByteArray();
  }
}