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
import org.unlaxer.tinyexpression.parser.FormulaParser;

import net.openhft.compiler.CompilerUtils;

public class JavaCodeCalculator extends PreConstructedCalculator<Float> implements JavaClassCreator{

	String className;
	public final String javaCode;

	Class<TokenBaseOperator<CalculationContext, Float>> loadFromJava;
	TokenBaseOperator<CalculationContext, Float> instance;

  public JavaCodeCalculator(Name name, String formula) {
    this(name,formula,null,true);
  }

  public JavaCodeCalculator(Name name, String formula , Path outputRootDirectory ,boolean randomize) {
    this(formula , 
        name.getName()+"_CalculatorClass"  +(randomize ? String.valueOf(Math.abs(new Random().nextLong())) :"" ), 
        outputRootDirectory);
  }

  public JavaCodeCalculator(String formula , String className) {
    this(formula,className,null);
  }

	@SuppressWarnings("unchecked")
	public JavaCodeCalculator(String formula , String className, Path outputRootDirectory) {
		super(formula , className);
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
			synchronized (CompilerUtils.CACHED_COMPILER) {
				loadFromJava =
						CompilerUtils.CACHED_COMPILER.loadFromJava(className, javaCode);
				instance = (TokenBaseOperator<CalculationContext, Float>) 
						loadFromJava.getDeclaredConstructor().newInstance();
			}
		} catch (ClassNotFoundException | InstantiationException |IllegalAccessException | IllegalArgumentException |
				InvocationTargetException | NoSuchMethodException | SecurityException e) {

		  System.err.print(javaCode);

			throw new RuntimeException(e);
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

	@Override
  public String javaCode() {
    return javaCode;
  }

}
