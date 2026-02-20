package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Iterator;
import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder.Kind;
import org.unlaxer.tinyexpression.parser.BooleanExpression;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.NumberExpression;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser.MethodAndParameters;
import org.unlaxer.tinyexpression.parser.StringExpression;

public class ParametersBuilder  {

	public static void buildParameter(SimpleJavaCodeBuilder builder, MethodAndParameters methodAndParameters , 
	    TinyExpressionTokens tinyExpressionTokens) {
	  buildParameter(builder, methodAndParameters.parameterTokens, tinyExpressionTokens);
	}
	
  public static void buildParameter(SimpleJavaCodeBuilder builder, List<Token> Parameters , 
        TinyExpressionTokens tinyExpressionTokens) {
		
		Iterator<Token> iterator = Parameters.iterator();
		
		while(iterator.hasNext()) {
			Token token = iterator.next();
			
			Parser parser = token.parser;
			if(parser instanceof NakedVariableParser) {
//				  NakedVariableBuilder.SINGLETON.build(builder, token , tinyExpressionTokens);
        NumberExpressionBuilder.SINGLETON.build(builder, token , tinyExpressionTokens);//デフォルトでnumberとする
			}else if(parser instanceof NumberExpression) {
				NumberExpressionBuilder.SINGLETON.build(builder, token , tinyExpressionTokens);
			}else if(parser instanceof BooleanExpression) {
				BooleanExpressionBuilder.SINGLETON.build(builder, token , tinyExpressionTokens);
			}else if (parser instanceof StringExpression) {
				ExpressionOrLiteral build = StringClauseBuilder.SINGLETON.build(token , tinyExpressionTokens);
        build.populateTo(builder, Kind.Function);
        builder.append(build.toString());
			}else {
				throw new IllegalArgumentException();
			}
			if(iterator.hasNext()) {
				builder.append(" , ");
			}
		}
	}
}