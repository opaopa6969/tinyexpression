package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Supplier;

import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder.Kind;
import org.unlaxer.tinyexpression.parser.BooleanExpression;
import org.unlaxer.tinyexpression.parser.NumberExpression;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser.MethodAndParameters;
import org.unlaxer.tinyexpression.parser.StringExpression;

public class SideEffectExpressionBuilder implements TokenCodeBuilder {

	
	public static SideEffectExpressionBuilder SINGLETON = new SideEffectExpressionBuilder();

	@Override
	public void build(SimpleJavaCodeBuilder builder, Token token , 
	    TinyExpressionTokens tinyExpressionTokens) {
		
		MethodAndParameters methodAndParameters = SideEffectExpressionParser.extract(token);
		
		String methodName = methodAndParameters.classNameAndIdentifier.getIdentifier();
		String className = 
		    tinyExpressionTokens.resolveJavaClass(
		        methodAndParameters.classNameAndIdentifier.getClassName()
		    );
		
				
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
		// old implementation : first parameter is default returning value
    // new implementation : "returning as type default xxx". xxx is default returning value
		//　　　　　　　　　　　　　　　　 : if returning clause is not exists , then first parameter is default returning value
		
		
		Token returningToken = methodAndParameters.returningToken;
		Parser parser = returningToken.parser;
		TokenPrinter.output(returningToken, System.out);
		if(parser instanceof NumberExpression) {
		  NumberExpressionBuilder.SINGLETON.build(builder, returningToken , tinyExpressionTokens);
		}else if(parser instanceof StringExpression){
      StringExpressionBuilder.SINGLETON.build(builder, returningToken , tinyExpressionTokens);
		}else {
		  BooleanExpressionBuilder.SINGLETON.build(builder, returningToken , tinyExpressionTokens);
		}
		
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
				if(parser instanceof NakedVariableParser) {
				  NakedVariableBuilder.SINGLETON.build(builder, token , tinyExpressionTokens);
				}else if(parser instanceof NumberExpression) {
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