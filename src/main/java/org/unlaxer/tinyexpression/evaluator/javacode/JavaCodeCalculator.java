package org.unlaxer.tinyexpression.evaluator.javacode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.PreConstructedCalculator;
import org.unlaxer.tinyexpression.TokenBaseCalculator;
import org.unlaxer.tinyexpression.parser.FormulaParser;
import org.unlaxer.util.digest.MD5;

import net.openhft.compiler.CachedCompilerModifiedForByteCodeGetting.CompileResult;
import net.openhft.compiler.CompilerUtilsModifedForGettingByteCode;

public class JavaCodeCalculator extends PreConstructedCalculator<Float> implements JavaClassCreator, TokenBaseCalculator{

	String className;
	final String javaCode;
	final byte[] byteCode; 

	Class<TokenBaseCalculator> loadFromJava;
	TokenBaseCalculator instance;

  public JavaCodeCalculator(Name name, String formula, ClassLoader classLoader) {
    this(name.getName(),formula,null,classLoader);
  }

//  public JavaCodeCalculator(Name name, String formula , Path outputRootDirectory ,boolean randomize , ClassLoader classLoader) {
//    this(formula , 
//        name.getName()+"_CalculatorClass"  +(randomize ? String.valueOf(Math.abs(new Random().nextLong())) :"" ), 
//        outputRootDirectory ,classLoader);
//  }

  public JavaCodeCalculator(String formula , String className, ClassLoader classLoader) {
    this(formula,className,null, classLoader);
  }

	public JavaCodeCalculator(String formula , String className, Path outputRootDirectory , ClassLoader classLoader) {
		super(formula , className , true);
		this.className = className;
		
		TinyExpressionTokens tinyExpressionTokens = new TinyExpressionTokens(rootToken);
		
		javaCode = createJavaClass(className, tinyExpressionTokens);
		if(outputRootDirectory != null) {
		  try(BufferedWriter newBufferedWriter = Files.newBufferedWriter(outputRootDirectory.resolve(className+".java"))){
		    newBufferedWriter.write(javaCode);
		  } catch (IOException e1) {
		    e1.printStackTrace();
		  }
		}

		try {
      CalculatorAndByteCode calculator = compile(javaCode, className , classLoader);
      loadFromJava = calculator.calculatorCLass;
      instance = calculator.instance;
      byteCode = calculator.bytes;
		} catch (Throwable e) { 

		  System.err.print(javaCode);

			throw new RuntimeException(e);
		}
	}

	@Override
	public Parser getParser() {
		return Parser.get(FormulaParser.class);
	}

	@Override
	public TokenBaseCalculator getCalculatorOperator() {
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

  
  @SuppressWarnings("unchecked")
  static CalculatorAndByteCode compile(String javaCode , String className , ClassLoader classLoader){
    
    try {
      
      if(loaded(classLoader , className)) {
        
        var calculatorClass = (Class<TokenBaseCalculator>) classLoader.loadClass(className);
        TokenBaseCalculator instance = (TokenBaseCalculator) calculatorClass.getDeclaredConstructor().newInstance();
        
        synchronized (CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER) {
          CompileResult<Function<CalculationContext, Float>> loadFromJava =
              (CompileResult<Function<CalculationContext, Float>>) 
              CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER.loadFromJava(classLoader/*new ClassLoader() {}*/ , className, javaCode);
          
          byte[] byteCode = loadFromJava.byteCode;
          
          return new CalculatorAndByteCode(calculatorClass, instance,byteCode);
        }
        
      }else {
        
        synchronized (CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER) {
          CompileResult<TokenBaseCalculator> loadFromJava =
              (CompileResult<TokenBaseCalculator>) 
              CompilerUtilsModifedForGettingByteCode.CACHED_COMPILER.loadFromJava(classLoader , className, javaCode);
          TokenBaseCalculator instance = (TokenBaseCalculator) loadFromJava.loadedClass.getDeclaredConstructor().newInstance();
          byte[] byteCode = loadFromJava.byteCode;
          
          return new CalculatorAndByteCode((Class<TokenBaseCalculator>)instance.getClass(), instance,byteCode);
        }
      }
    } catch (ClassNotFoundException | InstantiationException |IllegalAccessException | IllegalArgumentException |
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
  
  static class CalculatorAndByteCode{
    public final Class<TokenBaseCalculator> calculatorCLass;
    public final TokenBaseCalculator instance;
    public final byte[] bytes;
    public CalculatorAndByteCode(Class<TokenBaseCalculator> calculatorCLass,
        TokenBaseCalculator contextCalculator, byte[] bytes) {
      super();
      this.calculatorCLass = calculatorCLass;
      this.instance = contextCalculator;
      this.bytes = bytes;
    }
  }

  @Override
  public String formulaHash() {
    return MD5.toHex(formula);
  }

  @Override
  public String byteCodeHash() {
    return null;
  }


}
