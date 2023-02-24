package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Supplier;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculator.CodeBuilder;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder.Kind;
import org.unlaxer.tinyexpression.parser.BooleanExpression;
import org.unlaxer.tinyexpression.parser.Expression;
import org.unlaxer.tinyexpression.parser.SideEffectStringToBooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.StringExpression;
import org.unlaxer.tinyexpression.parser.SideEffectStringToBooleanExpressionParser.MethodAndParameters;

public class SideEffectStringToBooleanExpressionBuilder implements CodeBuilder {
	
	public static SideEffectStringToBooleanExpressionBuilder SINGLETON = new SideEffectStringToBooleanExpressionBuilder();
	
	@Override
	public void build(SimpleJavaCodeBuilder builder, Token token) {
		// TODO Auto-generated method stub
		
		MethodAndParameters methodAndParameters = SideEffectStringToBooleanExpressionParser.extract(token);
		
		String className = methodAndParameters.classNameAndIdentifier.getClassName();
		String methodName = methodAndParameters.classNameAndIdentifier.getIdentifier();

		builder
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
			.setKind(Kind.Calculation)
			.appendCurrentFunctionName()
			.append(".map(_function->function.")
			.append(methodName)
			.append("(calculateContext , ");
		
		ParametersBuilder.buildParameter(builder, methodAndParameters);
		
		builder
		.append(")).orElse(");
		
	}
	
	static Supplier<Float> sampleSupplier = ()->{System.out.println("");return 10.0f;};
	static Function<Float,Float> sampleFunction= (x)->{x++;return x;};
	
	public static class ParametersBuilder {
		public static ParametersBuilder SINGLETON = new ParametersBuilder();
		
		public static void buildParameter(SimpleJavaCodeBuilder builder, MethodAndParameters methodAndParameters) {
			Iterator<Token> iterator = methodAndParameters.parameterTokens.iterator();
			
			while(iterator.hasNext()) {
				Token token = iterator.next();
				
				Parser parser = token.parser;
				if(parser instanceof Expression) {
					ExpressionBuilder.SINGLETON.build(builder, token);
				}else if(parser instanceof BooleanExpression) {
					BooleanBuilder.SINGLETON.build(builder, token.filteredChildren.get(0));
				}else if (parser instanceof StringExpression) {
					builder.append(StringClauseBuilder.SINGLETON.build(token).toString());
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
