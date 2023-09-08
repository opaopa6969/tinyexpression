package org.unlaxer.util;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

public class CloseableClassLoader extends URLClassLoader {
  
  static URL[] classPaths;
  
  static {
    String property = System.getProperty("java.class.path");
    System.out.println(property);
    String[] split = property.split(File.pathSeparator);
    classPaths = new URL[split.length];
    int index = 0;
    for (String path : split) {
      
      try {
        URL url = Paths.get(path).toUri().toURL();
        classPaths[index++] = url;
      } catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }
  }
  
  public CloseableClassLoader() {
    super(classPaths);
  }
  
  @Override
  public void close() {
    try {
      super.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
