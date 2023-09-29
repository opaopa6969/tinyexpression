package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Supplier;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder.Kind;
import org.unlaxer.tinyexpression.parser.SideEffectStringExpressionParser;
import org.unlaxer.tinyexpression.parser.SideEffectStringExpressionParser.MethodAndParameters;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpression;
import org.unlaxer.tinyexpression.parser.number.NumberExpression;
import org.unlaxer.tinyexpression.parser.string.StringExpression;

public class SideEffectStringExpressionBuilder implements TokenCodeBuilder {

	
	public static SideEffectStringExpressionBuilder SINGLETON = new SideEffectStringExpressionBuilder();

	@Override
	public void build(SimpleJavaCodeBuilder builder, Token token , TinyExpressionTokens tinyExpressionTokens) {
		
		MethodAndParameters methodAndParameters = 
		    SideEffectStringExpressionParser.extract(token , tinyExpressionTokens);
		
		String methodName = methodAndParameters.classNameAndIdentifier.getIdentifier();
		String className = methodAndParameters.classNameAndIdentifier.getClassName();
		
				
		builder
			//		java.util.Optional<WhiteListSetter> function1 = calculateContext.getObject(
			//			org.unlaxer.tinyexpression.evaluator.javacode.WhiteListSetter.class);
			.setKind(Kind.Function)
			.append("java.util.Optional<")
			.append(className)
			.append("> ")
			.appendCurrentFunctionName()
			.append(" = calculateContext.getObject(")
			.n()
			.incTab()
			.append(className)
			.append(".class);")
			.decTab()
			.n()
			// function1.map(_function->_function.setWhiteList(calculateContext, 1.0f)).orElse(1.0f)	:
			.setKind(Kind.Calculation)
			.appendCurrentFunctionName()
			.append(".map(_function->_function.")
			.append(methodName)
			.append("(calculateContext , ");
		
		ParametersBuilder.buildParameter(builder, methodAndParameters , tinyExpressionTokens);
		
		builder
			.append(")).orElse(");
		// first parameter is default returning value
		StringExpressionBuilder.SINGLETON.build(builder, methodAndParameters.parameterTokens.get(0) , tinyExpressionTokens);
		
		builder
			.append(")");
			;
		
	}

	static Supplier<Float> sampleSupplier = ()->{System.out.println("");return 10.0f;};
	static Function<Float,Float> sampleFunction= (x)->{x++;return x;};
	
	
	public static class ParametersBuilder  {

		
		public static ParametersBuilder SINGLETON = new ParametersBuilder();

		public static void buildParameter(SimpleJavaCodeBuilder builder, MethodAndParameters methodAndParameters , 
		    TinyExpressionTokens tinyExpressionTokens) {
			
			
			Iterator<Token> iterator = methodAndParameters.parameterTokens.iterator();
			
			while(iterator.hasNext()) {
				Token token = iterator.next();
				
				Parser parser = token.parser;
				if(parser instanceof NumberExpression) {
					NumberExpressionBuilder.SINGLETON.build(builder, token , tinyExpressionTokens);
				}else if(parser instanceof BooleanExpression) {
					BooleanExpressionBuilder.SINGLETON.build(builder, token , tinyExpressionTokens);
				}else if (parser instanceof StringExpression) {
					builder.append(StringClauseBuilder.SINGLETON.build(token , tinyExpressionTokens).toString());
				}else {
					throw new IllegalArgumentException();
				}
				if(iterator.hasNext()) {
					builder.append(" , ");
				}
			}
		}

		static Supplier<Float> sampleSupplier = ()->{System.out.println("");return 10.0f;};
		static Function<Float,Float> sampleFunction= (x)->{x++;return x;};
	}
	
}