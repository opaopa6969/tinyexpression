package org.unlaxer.tinyexpression.evaluator.javacode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.UnaryOperator;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.compiler.CompileError;
import org.unlaxer.compiler.CustomClassloaderJavaFileManager;
import org.unlaxer.compiler.JavaFileManagerContext;
import org.unlaxer.compiler.MemoryClassLoader;
import org.unlaxer.compiler.MemoryJavaFileManager;
import org.unlaxer.listener.TransactionListener;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.PreConstructedCalculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.TokenBaseCalculator;
import org.unlaxer.tinyexpression.TokenBaseOperator;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.FormulaParser;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationParser;
import org.unlaxer.util.digest.MD5;

//import sun.misc.Unsafe;

public class JavaCodeCalculatorV2 extends PreConstructedCalculator
    implements JavaClassCreator, TokenBaseCalculator {

  public final String className;
  public final String javaCode;
  public final String classNameWithHash;
  public final Class<?> returningClass;

  public final byte[] byteCode;
  final String formulaHash;
  final String byteCodeHash;
  final List<Calculator> dependsOns;
  Optional<Calculator> dependsOnBy;


  TokenBaseOperator<CalculationContext> operator;


  /**
   * from formula
   * @param name
   * @param formula
   * @param returningClass
   * @throws CompileError
   */
  public JavaCodeCalculatorV2(Name name, String formula , Class<?> returningClass) throws CompileError {
    this(name, formula, returningClass, (Path) null);
  }

  /**
   * from formula
   * @param name
   * @param formula
   * @param returningClass
   * @param outputRootDirectory
   * @throws CompileError
   */
  public JavaCodeCalculatorV2(Name name, String formula, Class<?> returningClass, Path outputRootDirectory) throws CompileError {
    this(name, formula, returningClass, Thread.currentThread().getContextClassLoader(), outputRootDirectory, true,
        new JavaFileManagerContext());
  }

  /**
   * from formula
   * @param name
   * @param formula
   * @param returningClass
   * @param classLoader
   * @throws CompileError
   */
  public JavaCodeCalculatorV2(Name name, String formula, Class<?> returningClass, ClassLoader classLoader) throws CompileError {
    this(name, formula, returningClass, classLoader, null, true, new JavaFileManagerContext());
  }

  /**
   * from formula
   * @param name
   * @param formula
   * @param returningClass
   * @param classLoader
   * @param outputRootDirectory
   * @param randomize
   * @param javaFileManagerContext
   * @throws CompileError
   */
  public JavaCodeCalculatorV2(Name name, String formula, Class<?> returningClass, ClassLoader classLoader, Path outputRootDirectory,
      boolean randomize, JavaFileManagerContext javaFileManagerContext) throws CompileError {
    this(formula, returningClass, name.getName() + "_CalculatorClass" + (randomize ? Math.abs(new Random().nextLong()) : ""),
        classLoader, outputRootDirectory, javaFileManagerContext);
  }

  /**
   * from formula
   * @param formula
   * @param returningClass
   * @param className
   * @param classLoader
   * @throws CompileError
   */
  public JavaCodeCalculatorV2(String formula, Class<?> returningClass, String className, ClassLoader classLoader) throws CompileError {
    this(formula, returningClass , className, classLoader, null, new JavaFileManagerContext());
  }

  /**
   * from formula
   * @param formula
   * @param returningClass
   * @param className
   * @param classLoader
   * @param outputRootDirectory
   * @param javaFileManagerContext
   * @throws CompileError
   */
  @SuppressWarnings("unchecked")
  public JavaCodeCalculatorV2(String formula, Class<?> returningClass, String className, ClassLoader classLoader,
      @Nullable Path outputRootDirectory,
      JavaFileManagerContext javaFileManagerContext) throws CompileError {
    super(formula, className,
        new SpecifiedExpressionTypes(ExpressionTypes._float,ExpressionTypes._float),
        true);

    SpecifiedExpressionTypes specifiedExpressionTypes =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);

    dependsOnBy = Optional.empty();
    dependsOns = new ArrayList<>();

    StringWriter output = new StringWriter();

    try {

      this.className = className;
      formulaHash = MD5.toHex(formula);

      TinyExpressionTokens tinyExpressionTokens = new TinyExpressionTokens(rootToken,specifiedExpressionTypes);
      this.returningClass = tinyExpressionTokens.expressionToken.getParser().expressionType().javaType();

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
              classLoader, fileManager , javaFileManagerContext);

          MemoryJavaFileManager memoryFileManager = new MemoryJavaFileManager(customClassloaderJavaFileManager , javaFileManagerContext);) {

        JavaCompiler.CompilationTask task = compiler.getTask(new PrintWriter(output), memoryFileManager, null,
            null, null, Arrays.asList(javaFileObject));

        boolean success = task.call();
//        System.out.println("Compilation " + (success ? "succeeded" : "failed"));
//        System.out.println(output.toString());

        if (success) {

          MemoryClassLoader memoryClassLoader = new MemoryClassLoader(memoryFileManager.getBytesByName(), classLoader);

          Class<?> clazz = memoryClassLoader.loadClass(classNameWithHash);

          operator = (TokenBaseOperator<CalculationContext>) clazz.getDeclaredConstructor().newInstance();

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
  public JavaCodeCalculatorV2(String formula, String javaCode, String className,
      byte[] byteCode, String byteCodeHash,
      ClassLoader classLoader) {
    super(formula, className,
        new SpecifiedExpressionTypes(ExpressionTypes._float,ExpressionTypes._float),
        false);
    this.className = className;
    this.classNameWithHash = null;
    this.javaCode = javaCode;
    this.byteCode = byteCode;
    this.byteCodeHash = byteCodeHash;

    dependsOnBy = Optional.empty();
    dependsOns = new ArrayList<>();


    formulaHash = MD5.toHex(formula);

    Class<TokenBaseOperator<CalculationContext>> calculatorClass = null;

    try {
      try {
        calculatorClass = (Class<TokenBaseOperator<CalculationContext>>) classLoader
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
      Method method = calculatorClass.getMethod("evaluate", CalculationContext.class,Token.class);
      this.returningClass = method.getReturnType();

      operator = (TokenBaseOperator<CalculationContext>) calculatorClass.getDeclaredConstructor()
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
  public JavaCodeCalculatorV2(String formula, String javaCode, String className,
      byte[] byteCode, String byteCodeHash,
      Class<TokenBaseOperator<CalculationContext>> calculatorClass, ClassLoader classLoader) {
    super(formula, className,
        new SpecifiedExpressionTypes(ExpressionTypes._float,ExpressionTypes._float),
        false);
    this.className = className;
    this.javaCode = javaCode;
    this.byteCode = byteCode;
    this.byteCodeHash = byteCodeHash;
    this.classNameWithHash = "";

    dependsOnBy = Optional.empty();
    dependsOns = new ArrayList<>();

    formulaHash = MD5.toHex(formula);

    try {
      Method method = calculatorClass.getMethod("evaluate", CalculationContext.class,Token.class);
      this.returningClass = method.getReturnType();

      operator = (TokenBaseOperator<CalculationContext>) calculatorClass.getDeclaredConstructor()
          .newInstance();
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
        | NoSuchMethodException | SecurityException e) {
      throw new RuntimeException(e);
    }
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
  public TokenBaseOperator<CalculationContext> getCalculatorOperator() {
    return operator;
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
  public Object evaluate(CalculationContext context, Token token) {
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
  public void setDependsOnBy(Calculator calculator) {
    dependsOnBy = Optional.of(calculator);
  }

  @Override
  public ExpressionType resultType() {
    return ExpressionTypes._float;
  }

  @Override
  public String returningTypeAsString() {
    return "float";
  }

  @Override
  public List<Calculator> dependsOns() {
    return Collections.emptyList();
  }

  @Override
  public Optional<Calculator> dependsOnBy() {
    return Optional.empty();
  }

  @Override
  public void before(CalculationContext calculationContext) {
  }

  @Override
  public void after(CalculationContext calculationContext) {
  }

  @Override
  public InstanceKind instanceKind() {
    return InstanceKind.fromSource;
  }

  @Override
  public Source source() {
    return new Source(formula);
  }
}