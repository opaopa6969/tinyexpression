package org.unlaxer.tinyexpression.evaluator.javacode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.function.UnaryOperator;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.PreConstructedCalculator;
import org.unlaxer.tinyexpression.TokenBaseOperator;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder.Kind;
import org.unlaxer.tinyexpression.parser.FormulaParser;

import net.openhft.compiler.CachedCompilerModifiedForByteCodeGetting.CompileResult;
import net.openhft.compiler.CompilerUtils;
import net.openhft.compiler.CompilerUtilsModifedForGettingByteCode;

public class JavaCodeCalculatorV2 extends PreConstructedCalculator<Float> {

  public final String className;
  public final String javaCode;
  
  public final byte[] byteCode;

  TokenBaseOperator<CalculationContext, Float> instance;

  public JavaCodeCalculatorV2(Name name , String formula) {
    this(name,formula,(Path)null);
  }
  
  public JavaCodeCalculatorV2(Name name , String formula ,Path outputRootDirectory) {
    this(name , formula, Thread.currentThread().getContextClassLoader(),outputRootDirectory , true);
  }
  
  public JavaCodeCalculatorV2(Name name,String formula , ClassLoader classLoader) {
    this(name,formula,classLoader,null , true);
  }


  public JavaCodeCalculatorV2(Name name,String formula , ClassLoader classLoader, 
      Path outputRootDirectory ,boolean randomize) {
    this(formula , name.getName()+"_CalculatorClass"  + (randomize ?  Math.abs(new Random().nextLong()) :"") , classLoader , outputRootDirectory);
  }

  public JavaCodeCalculatorV2(String formula , String className , ClassLoader classLoader) {
    this(formula,className,classLoader,null);
  }

  @SuppressWarnings("unchecked")
  public JavaCodeCalculatorV2(String formula , String className , ClassLoader classLoader, Path outputRootDirectory) {
    super(formula , className);
    this.className = className;
    javaCode = createJavaClass(className, rootToken);
    if(outputRootDirectory != null) {
      try(BufferedWriter newBufferedWriter = Files.newBufferedWriter(outputRootDirectory.resolve(className+".java"))){
        newBufferedWriter.write(javaCode);
      } catch (IOException e1) {
        e1.printStackTrace();
      }
    }
    
    CompileResult<TokenBaseOperator<CalculationContext, Float>> loadFromJava;
    try {
      
      if(loaded(classLoader , className)) {
        
        var calculatorClass = (Class<TokenBaseOperator<CalculationContext, Float>>) classLoader.loadClass(className);
        instance = (TokenBaseOperator<CalculationContext, Float>) calculatorClass.getDeclaredConstructor().newInstance();
        
        loadFromJava =
            (CompileResult<TokenBaseOperator<CalculationContext, Float>>) 
//            CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER.loadFromJava(new ClassLoader() {} , className, javaCode);
            CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER.loadFromJava(classLoader , className, javaCode);
      }else {
        
        synchronized (CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER) {
          loadFromJava =
              (CompileResult<TokenBaseOperator<CalculationContext, Float>>) 
              CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER.loadFromJava(className, javaCode , classLoader);
          instance = (TokenBaseOperator<CalculationContext, Float>) loadFromJava.loadedClass.getDeclaredConstructor().newInstance();
        }
      }
      
    } catch (ClassNotFoundException | InstantiationException |IllegalAccessException | IllegalArgumentException |
        InvocationTargetException | NoSuchMethodException | SecurityException e) {

      throw new RuntimeException(e);
    }
    byteCode = loadFromJava.byteCode;
  }
  
  @SuppressWarnings("unchecked")
  public JavaCodeCalculatorV2(String formula , String javaCode , String className , byte[] byteCode , ClassLoader classLoader) {
    super(formula , className);
    this.className = className;
    this.javaCode = javaCode;
    this.byteCode = byteCode;
    
    Class<TokenBaseOperator<CalculationContext, Float>> calculatorClass = null;
    
    try {
        try {
          calculatorClass = (Class<TokenBaseOperator<CalculationContext, Float>>) classLoader.loadClass(className);
          
        } catch (ClassNotFoundException e) {
          
          calculatorClass = CompilerUtils.defineClass(classLoader , className, byteCode);
        } 
        instance = (TokenBaseOperator<CalculationContext, Float>) calculatorClass.getDeclaredConstructor().newInstance();
        
    } catch (InstantiationException |IllegalAccessException | IllegalArgumentException |
        InvocationTargetException | NoSuchMethodException | SecurityException e) {

      throw new RuntimeException(e);
    }
  }
  
  static boolean loaded(ClassLoader classLoader , String className) {
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
    return instance;
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

  String createJavaClass(String className, Token rootToken) {

    SimpleJavaCodeBuilder builder = new SimpleJavaCodeBuilder();

    String CalculationContextName = CalculationContext.class.getName();
    builder
      .setKind(Kind.Main)
      .line("import org.unlaxer.Token;")
      .line("import "+CalculationContextName+";")
      .line("import org.unlaxer.tinyexpression.TokenBaseOperator;")
      .n()
      .append("public class ")
      .append(className)
      .append(" implements TokenBaseOperator<"+CalculationContextName+", Float>{")
      .n()
      .n()
      .setKind(Kind.Function)
      .incTab()
      .line("@Override")
      .line("public Float evaluate("+CalculationContextName+" calculateContext , Token token) {")
      .setKind(Kind.Calculation)
      .incTab()
      .line("float answer = (float) ")
      .n();

    NumberExpressionBuilder.SINGLETON.build(builder, rootToken);

    builder
      .setKind(Kind.Calculation)
      .n()
      .line(";")
      .line("return answer;")
      .decTab()
      .line("}")
      .decTab()
      .setKind(Kind.Main);


    String code = builder.toString();
    return code;

  }

  public interface CodeBuilder {
    public void build(SimpleBuilder builder, Token token);
  }
  
  @Override
  public String javaCode() {
    return javaCode;
  }

}
