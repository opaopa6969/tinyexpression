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
import org.unlaxer.tinyexpression.TokenBaseCalculator;
import org.unlaxer.tinyexpression.TokenBaseOperator;
import org.unlaxer.tinyexpression.parser.FormulaParser;
import org.unlaxer.util.CloseableClassLoader;
import org.unlaxer.util.digest.MD5;

import net.openhft.compiler.CachedCompilerModifiedForByteCodeGetting.CompileResult;
import net.openhft.compiler.CompilerUtils;
import net.openhft.compiler.CompilerUtilsModifedForGettingByteCode;

public class JavaCodeCalculatorV2 extends PreConstructedCalculator<Float> implements JavaClassCreator , TokenBaseCalculator{

  public final String className;
  public final String javaCode;
  public final String classNameWithHash;
  
  public final byte[] byteCode;
  final String formulaHash;
  final String byteCodeHash;

  TokenBaseOperator<CalculationContext, Float> operator;

  public JavaCodeCalculatorV2(Name name , String formula) {
    this(name,formula,(Path)null);
  }
  
  public JavaCodeCalculatorV2(Name name , String formula ,Path outputRootDirectory) {
    this(name , formula, Thread.currentThread().getContextClassLoader(), outputRootDirectory , true);
  }
  public JavaCodeCalculatorV2(Name name,String formula , ClassLoader classLoader) {
    this(name , formula,classLoader,  null , true);
  }

  public JavaCodeCalculatorV2(Name name,String formula , ClassLoader classLoader,
      Path outputRootDirectory ,boolean randomize) {
    this(formula , name.getName()+"_CalculatorClass"  + (randomize ?  Math.abs(new Random().nextLong()) :"") , 
        classLoader ,  outputRootDirectory);
  }

  public JavaCodeCalculatorV2(String formula , String className , ClassLoader classLoader) {
    this(formula,className,classLoader,null);
  }
  
  

  public JavaCodeCalculatorV2(String formula , String className , ClassLoader classLoader, Path outputRootDirectory) {
    super(formula , className , true);
    
    try {
        
        this.className = className;
        formulaHash = MD5.toHex(formula);
        
        TinyExpressionTokens tinyExpressionTokens = new TinyExpressionTokens(rootToken);
        
        classNameWithHash = className+"_"+formulaHash;
        javaCode = createJavaClass(classNameWithHash, tinyExpressionTokens);
        if(outputRootDirectory != null) {
          try(BufferedWriter newBufferedWriter = Files.newBufferedWriter(outputRootDirectory.resolve(classNameWithHash+".java"))){
            newBufferedWriter.write(javaCode);
          } catch (IOException e1) {
            e1.printStackTrace();
          }
        }
        
        CompileResultAndOperator compile1 = compile(classNameWithHash, javaCode , classLoader);
        byteCode = compile1.compileResult.byteCode;
        operator = compile1.operator;
        byteCodeHash = MD5.toHex(compile1.compileResult.byteCode);
        
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
    
  }

  static class CompileResultAndOperator{
    public final CompileResult<TokenBaseOperator<CalculationContext, Float>> compileResult;
    public final TokenBaseOperator<CalculationContext, Float> operator;
    public CompileResultAndOperator(CompileResult<TokenBaseOperator<CalculationContext, Float>> compileResult,
        TokenBaseOperator<CalculationContext, Float> operator) {
      super();
      this.compileResult = compileResult;
      this.operator = operator;
    }
  }
  
  @SuppressWarnings("unchecked")
  private CompileResultAndOperator compile(String className, String javaSourceCode , 
      ClassLoader classLoader) {
    CompileResult<TokenBaseOperator<CalculationContext, Float>> compileResult;
    TokenBaseOperator<CalculationContext, Float> tokenBaseOperator;
    
    try {
      synchronized (CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER) {
  
        if(loaded(classLoader , className)) {
          
          var calculatorClass = (Class<TokenBaseOperator<CalculationContext, Float>>) classLoader.loadClass(className);
          tokenBaseOperator = (TokenBaseOperator<CalculationContext, Float>) calculatorClass.getDeclaredConstructor().newInstance();
          
          compileResult = CompileResultCache.get(className);
              try (CloseableClassLoader closeableClassLoader = new CloseableClassLoader()){
                compileResult =
                    (CompileResult<TokenBaseOperator<CalculationContext, Float>>) 
  //            CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER.loadFromJava(new ClassLoader() {} , className, javaCode);
                    
                    CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER.loadFromJava(closeableClassLoader , className, javaSourceCode);
                
                CompileResultCache.set(className, compileResult);
                
              }catch (Throwable e) {
                e.printStackTrace();
                compileResult = CompileResultCache.get(className);
              }
        }else {
          
            
            try {
  
            compileResult =
                (CompileResult<TokenBaseOperator<CalculationContext, Float>>) 
                CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER.loadFromJava(className, javaSourceCode , classLoader);
            
            CompileResultCache.set(className, compileResult);
  
            }catch (Throwable e) {
              e.printStackTrace();
              compileResult = CompileResultCache.get(className);
            }
            System.out.println("c:" + compileResult);
            System.out.println("l:" + compileResult.loadedClass);
            tokenBaseOperator = (TokenBaseOperator<CalculationContext, Float>) compileResult.loadedClass.getDeclaredConstructor().newInstance();
          }
      }
      
    } catch (ClassNotFoundException | InstantiationException |IllegalAccessException | IllegalArgumentException |
        InvocationTargetException | NoSuchMethodException | SecurityException e) {

      throw new RuntimeException(e);
    }
    return  new CompileResultAndOperator(compileResult, tokenBaseOperator);
  }
  
  @SuppressWarnings("unchecked")
  public JavaCodeCalculatorV2(String formula , String javaCode , String className , byte[] byteCode , 
      String byteCodeHash,ClassLoader classLoader) {
    super(formula , className , false);
    this.className = className;
    this.classNameWithHash = null;
    this.javaCode = javaCode;
    this.byteCode = byteCode;
    this.byteCodeHash = byteCodeHash;
    
    formulaHash = MD5.toHex(formula);
    
    Class<TokenBaseOperator<CalculationContext, Float>> calculatorClass = null;
    
    try {
        try {
          calculatorClass = (Class<TokenBaseOperator<CalculationContext, Float>>) classLoader.loadClass(className);
          
        } catch (ClassNotFoundException e) {
          
          try {
            calculatorClass = CompilerUtils.defineClass(classLoader , className, byteCode);
          }catch (Throwable e2) {
            e2.printStackTrace();
            try {
              calculatorClass = CompilerUtils.defineClass(null , className, byteCode);
            }catch (Throwable e3) {
              e3.printStackTrace();
              calculatorClass = CompilerUtils.defineClass(null , null, byteCode);
            }
          }
        } 
        operator = (TokenBaseOperator<CalculationContext, Float>) calculatorClass.getDeclaredConstructor().newInstance();
        
    } catch (InstantiationException |IllegalAccessException | IllegalArgumentException |
        InvocationTargetException | NoSuchMethodException | SecurityException | NoClassDefFoundError e) {

      throw new RuntimeException(e);
    }
  }
  
  public JavaCodeCalculatorV2(String formula , String javaCode , String className , byte[] byteCode , 
      String byteCodeHash, Class<TokenBaseOperator<CalculationContext, Float>> calculatorClass , ClassLoader classLoader) {
    super(formula , className , false);
    this.className = className;
    this.javaCode = javaCode;
    this.byteCode = byteCode;
    this.byteCodeHash = byteCodeHash;
    this.classNameWithHash ="";
    
    formulaHash = MD5.toHex(formula);
    
    try {
      operator = (TokenBaseOperator<CalculationContext, Float>) calculatorClass.getDeclaredConstructor().newInstance();
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
        | NoSuchMethodException | SecurityException e) {
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

}
