package org.unlaxer.compiler;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.unlaxer.util.Try;
import org.unlaxer.util.function.Unchecked;

public class CompileContext implements Closeable{

  public final MemoryClassLoader memoryClassLoader;
  public final ClassLoader classLoader;
  public final StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.getDefault(),
      StandardCharsets.UTF_8);
  
  public final CustomClassloaderJavaFileManager customClassloaderJavaFileManager;
  public final MemoryJavaFileManager memoryFileManager;
  public final JavaFileManagerContext javaFileManagerContext;
  
  final Path outputPath;
  
  final Set<JavaFileObject> compiledClassJavaFileObjects;

  public CompileContext(ClassLoader classLoader, JavaFileManagerContext javaFileManagerContext) {
    this(classLoader,null,javaFileManagerContext);
  }
  
  public CompileContext(ClassLoader classLoader,Path outputPath , JavaFileManagerContext javaFileManagerContext) {
    super();
    this.memoryClassLoader = new MemoryClassLoader(classLoader);
    this.classLoader = classLoader;
    this.outputPath = outputPath;
    this.javaFileManagerContext = javaFileManagerContext;
    
    customClassloaderJavaFileManager = new CustomClassloaderJavaFileManager(
        memoryClassLoader, fileManager , javaFileManagerContext);
    memoryFileManager = new MemoryJavaFileManager(customClassloaderJavaFileManager , javaFileManagerContext);
    compiledClassJavaFileObjects = new HashSet<>();
  }

  public void putAll(Map<? extends String, ? extends byte[]> m) {
    memoryClassLoader.putAll(m);
  }
  
  // if class path changes , then reload.
  static JavaCompiler compiler;
  static {
    reset();
  }

  // this code import from OpenHFT/Java-Runtime-Compiler
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
    
    JavaFileObject javaFileObjectForJava = new MemoryJavaFileObjectForJava(className, javaSourceCode);

    StringWriter output = new StringWriter();

    try {

      JavaCompiler.CompilationTask task = compiler.getTask(
          new PrintWriter(output), memoryFileManager, null,
          null, null, Arrays.asList(javaFileObjectForJava));

      boolean success = task.call();

      if (success) {

        putAll(memoryFileManager.getBytesByName());

        Class<?> clazz = memoryClassLoader.loadClass(className.fullName());
        byte[] bytes = memoryClassLoader.getBytes(className.fullName());
        ClassAndByteCode classAndByteCode = new ClassAndByteCode(clazz,
            bytes);

        memoryFileManager.setJavaFileOBjectForClass(className.fullName,new MemoryJavaFileObjectForClass(className, bytes));
        
        return Try.immediatesOf(classAndByteCode);

      } else {
        return Try.immediatesOf(new CompileError(output.toString()));
      }
    } catch (Exception e) {
      return Try.immediatesOf(new CompileError(output.toString(), e));
    }
  }
  
  @Override
  public void close() throws IOException {
    Unchecked.run(()->fileManager.close());
    Unchecked.run(()->customClassloaderJavaFileManager.close());
    Unchecked.run(()->memoryFileManager.close());
  }
  
  public Optional<Path> outputPath(){
    
    return Optional.ofNullable(outputPath);
  }
  
  
//private static final Method DEFINE_CLASS_METHOD;
//static {
//    try {
//        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
//        theUnsafe.setAccessible(true);
//        Unsafe u = (Unsafe) theUnsafe.get(null);
//        DEFINE_CLASS_METHOD = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
//        try {
//            Field f = AccessibleObject.class.getDeclaredField("override");
//            long offset = u.objectFieldOffset(f);
//            u.putBoolean(DEFINE_CLASS_METHOD, offset, true);
//        } catch (NoSuchFieldException e) {
//            DEFINE_CLASS_METHOD.setAccessible(true);
//        }
//    } catch (NoSuchMethodException | IllegalAccessException | NoSuchFieldException e) {
//        throw new AssertionError(e);
//    }
//}

//public static Class defineClass(ClassLoader classLoader, @NotNull String className, @NotNull byte[] bytes) {
////return new CustomClassLoader().defineClass(className, bytes);
//try {
//    return (Class) DEFINE_CLASS_METHOD.invoke(classLoader, className, bytes, 0, bytes.length);
//} catch (IllegalAccessException e) {
//    throw new AssertionError(e);
//} catch (InvocationTargetException e) {
//    //noinspection ThrowInsideCatchBlockWhichIgnoresCaughtException
//    throw new AssertionError(e.getCause());
//}
//}

}