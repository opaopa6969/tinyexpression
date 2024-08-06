package org.unlaxer.compiler;

import java.net.URI;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

public class MemoryJavaFileObjectForJava extends SimpleJavaFileObject{

  String javaSourceCode;
  
  protected MemoryJavaFileObjectForJava(ClassName className , String javaSourceCode) {
    super(URI.create("string:///" + className.name() + ".java"), JavaFileObject.Kind.SOURCE);
    this.javaSourceCode = javaSourceCode;
  }
  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) {
    return javaSourceCode;
  }
}