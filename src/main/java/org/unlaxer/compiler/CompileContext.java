package org.unlaxer.compiler;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.unlaxer.util.Try;
import org.unlaxer.util.function.Unchecked;

public class CompileContext implements Closeable{

  public final MemoryClassLoader memoryClassLoader;
  public final ClassLoader classLoader;
  final StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.getDefault(),
      StandardCharsets.UTF_8);
  
  final CustomClassloaderJavaFileManager customClassloaderJavaFileManager;
  final MemoryJavaFileManager memoryFileManager;

  public CompileContext(ClassLoader classLoader) {
    super();
    this.memoryClassLoader = new MemoryClassLoader(classLoader);
    this.classLoader = classLoader;
    
    customClassloaderJavaFileManager = new CustomClassloaderJavaFileManager(
        classLoader, fileManager);
    memoryFileManager = new MemoryJavaFileManager(customClassloaderJavaFileManager);
  }

  public void putAll(Map<? extends String, ? extends byte[]> m) {
    memoryClassLoader.putAll(m);
  }
  
  // if class path changes , then reload.
  static JavaCompiler compiler;
  static {
    reset();
  }

  private static void reset() {
    compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
        try {
            Class<?> javacTool = Class.forName("com.sun.tools.javac.api.JavacTool");
            Method create = javacTool.getMethod("create");
            compiler = (JavaCompiler) create.invoke(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
  }

  

  public Try<ClassAndByteCode> compile(ClassName className , String javaSourceCode) {
    
    JavaFileObject javaFileObject = createJavaFileObject(className, javaSourceCode);

    StringWriter output = new StringWriter();

    try {

      JavaCompiler.CompilationTask task = compiler.getTask(new PrintWriter(output), memoryFileManager, null,
          null, null, Arrays.asList(javaFileObject));

      boolean success = task.call();

      if (success) {

        putAll(memoryFileManager.getBytesByName());

        Class<?> clazz = memoryClassLoader.loadClass(className.fullName());
        ClassAndByteCode classAndByteCode = new ClassAndByteCode(clazz,
            memoryClassLoader.getBytes(className.fullName()));

        return Try.immediatesOf(classAndByteCode);

      } else {
        return Try.immediatesOf(new CompileError(output.toString()));
      }
    } catch (Exception e) {
      return Try.immediatesOf(new CompileError(output.toString(), e));
    }
  }
  
  public static JavaFileObject createJavaFileObject(ClassName className , String javaSourceCode) {
    JavaFileObject javaFileObject = new SimpleJavaFileObject(
        URI.create("string:///" + className.name() + ".java"), JavaFileObject.Kind.SOURCE) {
      @Override
      public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return javaSourceCode;
      }
    };
    return javaFileObject;
  }

  @Override
  public void close() throws IOException {
    Unchecked.run(()->fileManager.close());
    Unchecked.run(()->customClassloaderJavaFileManager.close());
    Unchecked.run(()->memoryFileManager.close());
  }
}