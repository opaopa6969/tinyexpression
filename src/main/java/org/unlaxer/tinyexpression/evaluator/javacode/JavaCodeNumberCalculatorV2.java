package org.unlaxer.tinyexpression.evaluator.javacode;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.UnaryOperator;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.compiler.CustomClassloaderJavaFileManager;
import org.unlaxer.compiler.JavaFileManagerContext;
import org.unlaxer.listener.TransactionListener;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.PreConstructedNumberCalculator;
import org.unlaxer.tinyexpression.TokenBaseCalculator;
import org.unlaxer.tinyexpression.TokenBaseOperator;
import org.unlaxer.tinyexpression.parser.FormulaParser;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationParser;
import org.unlaxer.util.digest.MD5;

//import sun.misc.Unsafe;

public class JavaCodeNumberCalculatorV2 extends PreConstructedNumberCalculator
    implements JavaClassCreator, TokenBaseCalculator<Float> {

  public final String className;
  public final String javaCode;
  public final String classNameWithHash;

  public final byte[] byteCode;
  final String formulaHash;
  final String byteCodeHash;
  final List<Calculator<?>> dependsOns;
  Optional<Calculator<?>> dependsOnBy;

  TokenBaseOperator<CalculationContext, Float> operator;

  // constructors for source code

  public JavaCodeNumberCalculatorV2(Name name, String formula) throws CompileError {
    this(name, formula, (Path) null);
  }

  public JavaCodeNumberCalculatorV2(Name name, String formula, Path outputRootDirectory) throws CompileError {
    this(name, formula, Thread.currentThread().getContextClassLoader(), outputRootDirectory, true,
        new JavaFileManagerContext());
  }

  public JavaCodeNumberCalculatorV2(Name name, String formula, ClassLoader classLoader) throws CompileError {
    this(name, formula, classLoader, null, true, new JavaFileManagerContext());
  }

  public JavaCodeNumberCalculatorV2(Name name, String formula, ClassLoader classLoader, Path outputRootDirectory,
      boolean randomize, JavaFileManagerContext javaFileManagerContext) throws CompileError {
    this(formula, name.getName() + "_CalculatorClass" + (randomize ? Math.abs(new Random().nextLong()) : ""),
        classLoader, outputRootDirectory, javaFileManagerContext);
  }

  public JavaCodeNumberCalculatorV2(String formula, String className, ClassLoader classLoader) throws CompileError {
    this(formula, className, classLoader, null, new JavaFileManagerContext());
  }


  /**
   * from formula
   * @param formula
   * @param className
   * @param classLoader
   * @param outputRootDirectory
   * @param javaFileManagerContext
   * @throws CompileError
   */
  @SuppressWarnings("unchecked")
  public JavaCodeNumberCalculatorV2(String formula, String className, ClassLoader classLoader,
      @Nullable Path outputRootDirectory,
      JavaFileManagerContext javaFileManagerContext) throws CompileError {
    super(formula, className, true);
    
    dependsOnBy = Optional.empty();
    dependsOns = new ArrayList<>();

    StringWriter output = new StringWriter();

    try {

      this.className = className;
      formulaHash = MD5.toHex(formula);

      TinyExpressionTokens tinyExpressionTokens = new TinyExpressionTokens(rootToken);

      classNameWithHash = className + "_" + formulaHash;
      javaCode = createJavaClass(classNameWithHash, tinyExpressionTokens);

      JavaFileObject javaFileObject = new SimpleJavaFileObject(
          URI.create("string:///" + classNameWithHash + ".java"), JavaFileObject.Kind.SOURCE) {
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
          return javaCode;
        }
      };

      if (outputRootDirectory != null) {
        try (BufferedWriter newBufferedWriter = Files
            .newBufferedWriter(outputRootDirectory.resolve(classNameWithHash + ".java"))) {
          newBufferedWriter.write(javaCode);
        } catch (IOException e1) {
          e1.printStackTrace();
        }
      }

      try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.getDefault(),
          StandardCharsets.UTF_8);

          CustomClassloaderJavaFileManager customClassloaderJavaFileManager = new CustomClassloaderJavaFileManager(
              classLoader, fileManager);

          MemoryJavaFileManager memoryFileManager = new MemoryJavaFileManager(customClassloaderJavaFileManager);) {

        JavaCompiler.CompilationTask task = compiler.getTask(new PrintWriter(output), memoryFileManager, null,
            null, null, Arrays.asList(javaFileObject));

        boolean success = task.call();
//        System.out.println("Compilation " + (success ? "succeeded" : "failed"));
//        System.out.println(output.toString());

        if (success) {

          MemoryClassLoader memoryClassLoader = new MemoryClassLoader(memoryFileManager.getClassBytes(), classLoader);

          Class<?> clazz = memoryClassLoader.loadClass(classNameWithHash);

          operator = (TokenBaseOperator<CalculationContext, Float>) clazz.getDeclaredConstructor().newInstance();

          byteCode = memoryClassLoader.getBytes(classNameWithHash);
          byteCodeHash = MD5.toHex(byteCode);
        } else {
          throw new CompileError(output.toString());
        }
      }
    } catch (Throwable e) {
      throw new RuntimeException(output.toString(), e);
    }
  }

  /**
   * from bytecode
   * 
   * @param formula
   * @param javaCode
   * @param className
   * @param byteCode
   * @param byteCodeHash
   * @param classLoader
   */
  @SuppressWarnings("unchecked")
  public JavaCodeNumberCalculatorV2(String formula, String javaCode, String className, byte[] byteCode, String byteCodeHash,
      ClassLoader classLoader) {
    super(formula, className, false);
    this.className = className;
    this.classNameWithHash = null;
    this.javaCode = javaCode;
    this.byteCode = byteCode;
    this.byteCodeHash = byteCodeHash;
    
    dependsOnBy = Optional.empty();
    dependsOns = new ArrayList<>();


    formulaHash = MD5.toHex(formula);

    Class<TokenBaseOperator<CalculationContext, Float>> calculatorClass = null;

    try {
      try {
        calculatorClass = (Class<TokenBaseOperator<CalculationContext, Float>>) classLoader
            .loadClass(className);

      } catch (ClassNotFoundException e) {

        try {
          calculatorClass = defineClass(classLoader, className, byteCode);
        } catch (Throwable e2) {
          e2.printStackTrace();
          try {
            calculatorClass = defineClass(null, className, byteCode);
          } catch (Throwable e3) {
            e3.printStackTrace();
            calculatorClass = defineClass(null, null, byteCode);
          }
        }
      }
      operator = (TokenBaseOperator<CalculationContext, Float>) calculatorClass.getDeclaredConstructor()
          .newInstance();

    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
        | NoSuchMethodException | SecurityException | NoClassDefFoundError e) {

      throw new RuntimeException(e);
    }
  }
  
  /**
   * from bytecode
   * @param formula
   * @param javaCode
   * @param className
   * @param byteCode
   * @param byteCodeHash
   * @param calculatorClass
   * @param classLoader
   */
  public JavaCodeNumberCalculatorV2(String formula, String javaCode, String className, byte[] byteCode, String byteCodeHash,
      Class<TokenBaseOperator<CalculationContext, Float>> calculatorClass, ClassLoader classLoader) {
    super(formula, className, false);
    this.className = className;
    this.javaCode = javaCode;
    this.byteCode = byteCode;
    this.byteCodeHash = byteCodeHash;
    this.classNameWithHash = "";

    formulaHash = MD5.toHex(formula);
    
    dependsOnBy = Optional.empty();
    dependsOns = new ArrayList<>();

    try {
      operator = (TokenBaseOperator<CalculationContext, Float>) calculatorClass.getDeclaredConstructor()
          .newInstance();
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
        | NoSuchMethodException | SecurityException e) {
      throw new RuntimeException(e);
    }
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

//  private static final Method DEFINE_CLASS_METHOD;
//  static {
//      try {
//          Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
//          theUnsafe.setAccessible(true);
//          Unsafe u = (Unsafe) theUnsafe.get(null);
//          DEFINE_CLASS_METHOD = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
//          try {
//              Field f = AccessibleObject.class.getDeclaredField("override");
//              long offset = u.objectFieldOffset(f);
//              u.putBoolean(DEFINE_CLASS_METHOD, offset, true);
//          } catch (NoSuchFieldException e) {
//              DEFINE_CLASS_METHOD.setAccessible(true);
//          }
//      } catch (NoSuchMethodException | IllegalAccessException | NoSuchFieldException e) {
//          throw new AssertionError(e);
//      }
//  }

  
  @SuppressWarnings("rawtypes")
  public static Class defineClass(@NotNull String className, @NotNull byte[] bytes) {
    return defineClass(Thread.currentThread().getContextClassLoader(), className, bytes);
  }

//  public static Class defineClass(ClassLoader classLoader, @NotNull String className, @NotNull byte[] bytes) {
////      return new CustomClassLoader().defineClass(className, bytes);
//        try {
//            return (Class) DEFINE_CLASS_METHOD.invoke(classLoader, className, bytes, 0, bytes.length);
//        } catch (IllegalAccessException e) {
//            throw new AssertionError(e);
//        } catch (InvocationTargetException e) {
//            //noinspection ThrowInsideCatchBlockWhichIgnoresCaughtException
//            throw new AssertionError(e.getCause());
//        }
//    }

  public static class CustomClassLoader extends ClassLoader {
    public Class<?> defineClass(String name, byte[] b) {
      return defineClass(name, b, 0, b.length);
    }
  }

  

  static boolean loaded(ClassLoader classLoader, String className) {
    try {
      classLoader.loadClass(className);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  @Override
  public Parser getParser() {
    return Parser.get(FormulaParser.class);
  }

  @Override
  public TokenBaseOperator<CalculationContext, Float> getCalculatorOperator() {
    return operator;
  }

  @Override
  public BigDecimal toBigDecimal(Float value) {
    return new BigDecimal(value);
  }

  @Override
  public float toFloat(Float value) {
    return value;
  }

  @Override
  public UnaryOperator<Token> tokenReduer() {
    return OperatorOperandTreeCreator.SINGLETON;
  }

  @Override
  public String javaCode() {
    return javaCode;
  }

  @Override
  public byte[] byteCode() {
    return byteCode;
  }

  @Override
  public Float evaluate(CalculationContext context, Token token) {
    return getCalculatorOperator().evaluate(context, token);
  }

  @Override
  public String formulaHash() {
    return formulaHash;
  }

  @Override
  public String byteCodeHash() {
    return byteCodeHash;
  }

  class MemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private final Map<String, ByteArrayJavaFileObject> classFiles = new HashMap<>();

    protected MemoryJavaFileManager(JavaFileManager fileManager) {
      super(fileManager);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
        FileObject sibling) throws IOException {
      ByteArrayJavaFileObject fileObject = new ByteArrayJavaFileObject(className, kind);
      classFiles.put(className, fileObject);
      return fileObject;
    }

    public Map<String, byte[]> getClassBytes() {
      Map<String, byte[]> classBytes = new HashMap<>();
      for (Map.Entry<String, ByteArrayJavaFileObject> entry : classFiles.entrySet()) {
        classBytes.put(entry.getKey(), entry.getValue().getBytes());
      }
      return classBytes;
    }
  }

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

  class MemoryClassLoader extends ClassLoader {
    private final Map<String, byte[]> classBytes;

    public MemoryClassLoader(Map<String, byte[]> classBytes, ClassLoader parent) {
      super(parent);
      this.classBytes = classBytes;
    }

    public byte[] getBytes(String name) {
      return classBytes.get(name);
    }

    protected Class<?> findClass(String name) throws ClassNotFoundException {
      byte[] bytes = classBytes.get(name);
      if (bytes == null) {
        throw new ClassNotFoundException(name);
      }
      return defineClass(name, bytes, 0, bytes.length);
    }
  }

  @Override
  public String className() {
    return className;
  }

  @Override
  public String classNameWithHash() {
    return classNameWithHash;
  }

  @Override
  public Collection<TransactionListener> transactionListeners() {
    return Set.of(Parser.get(VariableDeclarationParser.class));
  }
  
  @SuppressWarnings("rawtypes")
  public static Class defineClass(ClassLoader classLoader, String className, byte[] byteCode) {
    Method defineClassMethod;
    try {
      defineClassMethod = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class,
          int.class);
      defineClassMethod.setAccessible(true);
  
      return (Class) defineClassMethod.invoke(classLoader, className, byteCode, 0, byteCode.length);
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
        | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
  
  @SuppressWarnings("rawtypes")
  public static Class defineClassWithMethodHandle(ClassLoader classLoader, String className, byte[] byteCode) {
    
    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      MethodType methodType = MethodType.methodType(Class.class, String.class, byte[].class, int.class,
          int.class);
      MethodHandle virtual = lookup.findVirtual(ClassLoader.class, "defineClass", methodType);
      return (Class) virtual.invokeExact(classLoader, className, byteCode, 0, byteCode.length);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String returningTypeAsString() {
    return "float";
  }

  @Override
  public List<Calculator<?>> dependsOns() {
    return dependsOns;
  }

  @Override
  public Optional<Calculator<?>> dependsOnBy() {
    return dependsOnBy;
  }

  @Override
  public void setDependsOnBy(Calculator<?> calculator) {
    dependsOnBy = Optional.of(calculator);
  }

}
