package org.unlaxer.tinyexpression.evaluator.javacode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.compiler.ClassAndByteCode;
import org.unlaxer.compiler.ClassName;
import org.unlaxer.compiler.CompileContext;
import org.unlaxer.compiler.CompileError;
import org.unlaxer.compiler.InstanceAndByteCode;
import org.unlaxer.compiler.JavaFileManagerContext;
import org.unlaxer.listener.TransactionListener;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.SchemeAndIdentifier;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.PreConstructedObjectCalculator;
import org.unlaxer.tinyexpression.TokenBaseCalculator;
import org.unlaxer.tinyexpression.TokenBaseOperator;
import org.unlaxer.tinyexpression.parser.FormulaParser;
import org.unlaxer.tinyexpression.parser.javalang.CodeParser.CodeBlock;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationParser;
import org.unlaxer.util.Try;
import org.unlaxer.util.digest.MD5;

//import sun.misc.Unsafe;

public class JavaCodeCalculatorV3 extends PreConstructedObjectCalculator
    implements GeneralJavaClassCreator, TokenBaseCalculator<Object> {

  public final String className;
  public final String javaCode;
  public final String classNameWithHash;

  public final byte[] byteCode;
  final String formulaHash;
  final String byteCodeHash;
  final List<Calculator<?>> dependsOns;
  Optional<Calculator<?>> dependsOnBy;
  final List<InstanceAndByteCode> instanceAndByteCodeList;

  TokenBaseOperator<CalculationContext, Object> operator;

  // constructors for source code

  public JavaCodeCalculatorV3(Name name, String formula , SpecifiedExpressionTypes specifiedExpressionTypes) throws CompileError {
    this(name, formula, specifiedExpressionTypes ,  (Path) null);
  }

  public JavaCodeCalculatorV3(Name name, String formula, SpecifiedExpressionTypes specifiedExpressionTypes ,
      Path outputRootDirectory) throws CompileError {
    this(name, formula, specifiedExpressionTypes , Thread.currentThread().getContextClassLoader(), outputRootDirectory, true,
        new JavaFileManagerContext());
  }

  public JavaCodeCalculatorV3(Name name, String formula, SpecifiedExpressionTypes specifiedExpressionTypes , 
      ClassLoader classLoader) throws CompileError {
    this(name, formula, specifiedExpressionTypes , classLoader, null, true, new JavaFileManagerContext());
  }

  public JavaCodeCalculatorV3(Name name, String formula, 
      SpecifiedExpressionTypes specifiedExpressionTypes ,
      ClassLoader classLoader, Path outputRootDirectory,
      boolean randomize, JavaFileManagerContext javaFileManagerContext) throws CompileError {
    this(formula, name.getName() + "_CalculatorClass" + (randomize ? Math.abs(new Random().nextLong()) : ""),
        specifiedExpressionTypes , classLoader, outputRootDirectory, javaFileManagerContext);
  }

  public JavaCodeCalculatorV3(String formula, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes ,
      ClassLoader classLoader) throws CompileError {
    this(formula, className, specifiedExpressionTypes, classLoader, null, new JavaFileManagerContext());
  }
  
  /**
   * from formula
   * @param formula
   * @param className
   * @param specifiedExpressionTypes
   * @param classLoader
   * @param outputRootDirectory
   * @param javaFileManagerContext
   * @throws CompileError
   */
  @SuppressWarnings("unchecked")
  public JavaCodeCalculatorV3(String formula, String className, 
      SpecifiedExpressionTypes specifiedExpressionTypes ,
      ClassLoader classLoader,
      @Nullable Path outputRootDirectory,
      JavaFileManagerContext javaFileManagerContext) throws CompileError {
    super(formula, className, specifiedExpressionTypes ,  true);
    
    CompileContext compileContext = new CompileContext(classLoader,javaFileManagerContext);
    
    dependsOnBy = Optional.empty();
    dependsOns = new ArrayList<>();
    
    try {

      this.className = className;
      formulaHash = MD5.toHex(formula);

      TinyExpressionTokens tinyExpressionTokens = new TinyExpressionTokens(rootToken , specifiedExpressionTypes);
      
      instanceAndByteCodeList = createJavaFromCodedBlock(tinyExpressionTokens, compileContext);

//      ここでJavaCodesをあらかじめ先にコンパイルを行う。これもhashをつけなければならない。
//      ハッシュをつけるのでsideEffectのimport部分や直接クラス名を指定しているところをhash付きに置き換えてcreateJavaClassで
//      生成を行分ければならない。これは難しいのでpackage xxx.v1のようにpackage名でversion管理を行う
      
      classNameWithHash = className + "_" + formulaHash;
      javaCode = createJavaClass(classNameWithHash, tinyExpressionTokens , specifiedExpressionTypes);
      
      ClassName classNameObject = new ClassName(classNameWithHash);


      if (outputRootDirectory != null) {
        try (BufferedWriter newBufferedWriter = Files
            .newBufferedWriter(outputRootDirectory.resolve(classNameWithHash + ".java"))) {
          newBufferedWriter.write(javaCode);
        } catch (IOException e1) {
          e1.printStackTrace();
        }
      }
      
      Try<ClassAndByteCode> classOrError = compileContext.compile(classNameObject,javaCode);
      classOrError.throwIfMatch();

      ClassAndByteCode classAndByteCode = classOrError.get();
      operator = (TokenBaseOperator<CalculationContext, Object>) classAndByteCode.clazz.getDeclaredConstructor().newInstance();

      byteCode = classAndByteCode.bytes;
      byteCodeHash = MD5.toHex(byteCode);
    } catch (CompileError e) {
      throw e;
    } catch (Throwable e) {
      throw new CompileError(e);
    }
  }
  
  
  /**
   * from bytecode
   * @param formula
   * @param javaCode
   * @param className
   * @param resultType
   * @param byteCode
   * @param byteCodeHash
   * @param classNameAndByteCodeList
   * @param classLoader
   */
  @SuppressWarnings("unchecked")
  public JavaCodeCalculatorV3(String formula, String javaCode, String className, 
      SpecifiedExpressionTypes specifiedExpressionTypes,
      byte[] byteCode, String byteCodeHash, List<ClassNameAndByteCode> classNameAndByteCodeList,
      ClassLoader classLoader) {
    super(formula, className, specifiedExpressionTypes , false);
    this.className = className;
    this.classNameWithHash = null;
    this.javaCode = javaCode;
    this.byteCode = byteCode;
    this.byteCodeHash = byteCodeHash;
    
    dependsOnBy = Optional.empty();
    dependsOns = new ArrayList<>();
    
    instanceAndByteCodeList = classNameAndByteCodeList.stream()
      .map(classNameAndByteCode->{
        Object newInstance = newInstance(classNameAndByteCode.className, classNameAndByteCode.byteCode, classLoader);
        return new InstanceAndByteCode(newInstance, classNameAndByteCode.byteCode);
      }).toList();


    formulaHash = MD5.toHex(formula);

    Class<TokenBaseOperator<CalculationContext, Object>> calculatorClass = null;

    try {
      calculatorClass = (Class<TokenBaseOperator<CalculationContext, Object>>) loadClass(className, byteCode, classLoader);
      operator = (TokenBaseOperator<CalculationContext, Object>) calculatorClass.getDeclaredConstructor()
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
   * @param resultType
   * @param byteCode
   * @param byteCodeHash
   * @param calculatorClass
   * @param classNameAndByteCodeList
   * @param classLoader
   */
  public JavaCodeCalculatorV3(String formula, String javaCode, String className,
      SpecifiedExpressionTypes specifiedExpressionTypes,  
      byte[] byteCode, String byteCodeHash,Class<TokenBaseOperator<CalculationContext, Object>> calculatorClass, 
      List<ClassNameAndByteCode> classNameAndByteCodeList,
      ClassLoader classLoader) {
    super(formula, className, specifiedExpressionTypes ,  false);
    this.className = className;
    this.javaCode = javaCode;
    this.byteCode = byteCode;
    this.byteCodeHash = byteCodeHash;
    this.classNameWithHash = "";

    formulaHash = MD5.toHex(formula);
    
    dependsOnBy = Optional.empty();
    dependsOns = new ArrayList<>();
    
    instanceAndByteCodeList = classNameAndByteCodeList.stream()
        .map(classNameAndByteCode->{
          Object newInstance = newInstance(classNameAndByteCode.className, classNameAndByteCode.byteCode, classLoader);
          return new InstanceAndByteCode(newInstance, classNameAndByteCode.byteCode);
        }).toList();

    try {
      operator = (TokenBaseOperator<CalculationContext, Object>) calculatorClass.getDeclaredConstructor()
          .newInstance();
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
        | NoSuchMethodException | SecurityException e) {
      throw new RuntimeException(e);
    }
  }

  static Class<?> loadClass(String className , byte[] byteCode , ClassLoader classLoader){
    Class<?> clazz;
    try {
      try {
        clazz = classLoader.loadClass(className);

      } catch (ClassNotFoundException e) {

        try {
          clazz = defineClass(classLoader, className, byteCode);
        } catch (Throwable e2) {
          try {
            clazz = defineClass(null, className, byteCode);
          } catch (Throwable e3) {
            clazz = defineClass(null, null, byteCode);
          }
        }
      }
    } catch (IllegalArgumentException | SecurityException | NoClassDefFoundError e) {

      throw new RuntimeException(e);
    }
    return clazz;
  }
  
  
  static Object newInstance(String classNmae , byte[] byteCode , ClassLoader classLoader){
    try {
      Class<?> loadClass = loadClass(classNmae, byteCode, classLoader);
      return loadClass.getDeclaredConstructor().newInstance();
    }catch (Exception e) {
      throw new RuntimeException(e);
    }
    
  }
  
  
  static List<InstanceAndByteCode> createJavaFromCodedBlock(TinyExpressionTokens tinyExpressionTokens, CompileContext compileContext) {
    
    List<CodeBlock> codeBlocks = tinyExpressionTokens.codeBlocks;
    
    List<InstanceAndByteCode> instanceAndByteCodeList = new ArrayList<>();
    for (CodeBlock codeBlock : codeBlocks) {
      String code = codeBlock.code;
      SchemeAndIdentifier schemeAndIdentifier = codeBlock.schemeAndIdentifier;
      if(false ==schemeAndIdentifier.scheme.equalsIgnoreCase("java")){
        continue;
      }
      ClassName className = new ClassName(codeBlock.schemeAndIdentifier.idenitifier);
      
      Try<ClassAndByteCode> clazzOrError = compileContext.compile(className,code);
      clazzOrError.throwIfMatch();
      Class<?> clazz = clazzOrError.get().clazz;
      
      Object newInstance;
      try {
        newInstance = clazz.getDeclaredConstructor().newInstance();
        instanceAndByteCodeList.add(new InstanceAndByteCode(newInstance,clazzOrError.get().bytes));
      } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
          | NoSuchMethodException | SecurityException e) {
        throw new CompileError(e);
      }
    }
    return instanceAndByteCodeList;
  }

  @SuppressWarnings("rawtypes")
  public static Class defineClass(@NotNull String className, @NotNull byte[] bytes) {
    return defineClass(Thread.currentThread().getContextClassLoader(), className, bytes);
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
  public TokenBaseOperator<CalculationContext, Object> getCalculatorOperator() {
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

  @Override
  public void before(CalculationContext calculationContext) {
    instanceAndByteCodeList.stream().forEach(x -> calculationContext.set(x.object));
  }

  @Override
  public void after(CalculationContext calculationContext) {
  }

}
