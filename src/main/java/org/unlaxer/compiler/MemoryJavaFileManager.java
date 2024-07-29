package org.unlaxer.compiler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

public class MemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
  private final Map<String, ByteArrayJavaFileObject> classFiles = new HashMap<>();

  public MemoryJavaFileManager(JavaFileManager fileManager) {
    super(fileManager);
  }

  @Override
  public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
      FileObject sibling) throws IOException {
    ByteArrayJavaFileObject fileObject = new ByteArrayJavaFileObject(className, kind);
    classFiles.put(className, fileObject);
    return fileObject;
  }

  public Map<String, byte[]> getBytesByName() {
    Map<String, byte[]> classBytes = new HashMap<>();
    for (Map.Entry<String, ByteArrayJavaFileObject> entry : classFiles.entrySet()) {
      classBytes.put(entry.getKey(), entry.getValue().getBytes());
    }
    return classBytes;
  }
}