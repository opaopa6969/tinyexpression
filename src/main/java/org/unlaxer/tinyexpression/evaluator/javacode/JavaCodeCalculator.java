package org.unlaxer.tinyexpression.evaluator.javacode;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.Random;
import java.util.function.UnaryOperator;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.PreConstructedCalculator;
import org.unlaxer.tinyexpression.TokenBaseOperator;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder.Kind;
import org.unlaxer.tinyexpression.parser.FormulaParser;

import net.openhft.compiler.CompilerUtils;

public class JavaCodeCalculator extends PreConstructedCalculator<Float> {

	String className;
	public final String javaCode;

	Class<TokenBaseOperator<CalculationContext, Float>> loadFromJava;
	TokenBaseOperator<CalculationContext, Float> instance;

	public JavaCodeCalculator(String formula) {
		this(formula , "_CalculatorClass"  + Math.abs(new Random().nextLong()));
	}


	@SuppressWarnings("unchecked")
	public JavaCodeCalculator(String formula , String className) {
		super(formula , className);
		this.className = className;
		javaCode = createJavaClass(className, rootToken);
		
		try {
			synchronized (CompilerUtils.CACHED_COMPILER) {
				loadFromJava =
						CompilerUtils.CACHED_COMPILER.loadFromJava(className, javaCode);
				instance = (TokenBaseOperator<CalculationContext, Float>) 
						loadFromJava.getDeclaredConstructor().newInstance();
			}
		} catch (ClassNotFoundException | InstantiationException |IllegalAccessException | IllegalArgumentException |
				InvocationTargetException | NoSuchMethodException | SecurityException e) {

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
		return ASTCreator.SINGLETON;
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
			.incTab()
			.setKind(Kind.Calculation)
			.incTab()
			.line("float answer = (float) ")
			.n();

		ExpressionBuilder.SINGLETON.build(builder, rootToken);

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
		public void build(SimpleJavaCodeBuilder builder, Token token);
	}

}
