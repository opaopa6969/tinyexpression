package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Iterator;
import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculator.CodeBuilder;
import org.unlaxer.tinyexpression.parser.DivisionParser;
import org.unlaxer.tinyexpression.parser.ExpressionParser;
import org.unlaxer.tinyexpression.parser.FactorParser;
import org.unlaxer.tinyexpression.parser.IfExpressionParser;
import org.unlaxer.tinyexpression.parser.MatchExpressionParser;
import org.unlaxer.tinyexpression.parser.MinusParser;
import org.unlaxer.tinyexpression.parser.MultipleParser;
import org.unlaxer.tinyexpression.parser.NumberParser;
import org.unlaxer.tinyexpression.parser.PlusParser;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser;
import org.unlaxer.tinyexpression.parser.StringLengthParser;
import org.unlaxer.tinyexpression.parser.TermParser;
import org.unlaxer.tinyexpression.parser.ToNumParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.tinyexpression.parser.function.CosParser;
import org.unlaxer.tinyexpression.parser.function.MaxParser;
import org.unlaxer.tinyexpression.parser.function.MinParser;
import org.unlaxer.tinyexpression.parser.function.RandomParser;
import org.unlaxer.tinyexpression.parser.function.SinParser;
import org.unlaxer.tinyexpression.parser.function.SquareRootParser;
import org.unlaxer.tinyexpression.parser.function.TanParser;

public class ExpressionBuilder implements CodeBuilder {

	public static class CaseExpressionBuilder implements CodeBuilder{

		public static CaseExpressionBuilder SINGLETON = new CaseExpressionBuilder();

		public void build(SimpleJavaCodeBuilder builder, Token token) {

			List<Token> originalTokens = token.filteredChildren;
			Iterator<Token> iterator = originalTokens.iterator();

			while(iterator.hasNext()){
				Token caseFactor = iterator.next();

				Token booleanClause = caseFactor.filteredChildren.get(0);
				Token expression = caseFactor.filteredChildren.get(1);
				BooleanClauseBuilder.SINGLETON.build(builder, booleanClause);
				builder.append(" ? ");
				ExpressionBuilder.SINGLETON.build(builder, expression);
				builder
					.append(":")
					.n();
			}
		}
	}
	
	
	public static ExpressionBuilder SINGLETON = new ExpressionBuilder();

	public void build(SimpleJavaCodeBuilder builder, Token token) {

		Parser parser = token.parser;
		
		if(parser instanceof ExpressionParser) {
			
			token = token.filteredChildren.get(0);
			parser = token.parser;
			
			if (parser instanceof TermParser) {
				
				token = token.filteredChildren.get(0);
				parser = token.parser;
				
				if(parser instanceof FactorParser) {
					token = token.filteredChildren.get(0);
					parser = token.parser;
					
				}


}
		}
		
		if (parser instanceof PlusParser) {

			binaryOperate(builder, token, "+");

		} else if (parser instanceof MinusParser) {

			binaryOperate(builder, token, "-");

		} else if (parser instanceof MultipleParser) {

			binaryOperate(builder, token, "*");

		} else if (parser instanceof DivisionParser) {

			binaryOperate(builder, token, "/");

		} else if (parser instanceof NumberParser) {

			builder.append(String.valueOf(Float.parseFloat(token.tokenString.get()))+"f");

		} else if (parser instanceof VariableParser) {

			String variableName = token.tokenString.get().substring(1);

			builder.append("calculateContext.getValue(").w(variableName).append(").orElse(0f)");

		} else if (parser instanceof IfExpressionParser) {

			Token booleanClause = token.filteredChildren.get(0);
			Token factor1 = token.filteredChildren.get(1);
			Token factor2 = token.filteredChildren.get(2);

			/*
			 * BooleanClauseOperator.SINGLETON.evaluate(calculateContext, booleanClause)?
			 * factor1: factor2
			 */

			builder.append("(");

			BooleanClauseBuilder.SINGLETON.build(builder, booleanClause);

			builder.append(" ? ").n().incTab();
			build(builder, factor1);

			builder.append(":").n();
			build(builder, factor2);

			builder.decTab();

			builder.append(")");

		} else if (parser instanceof MatchExpressionParser) {

			Token caseExpression = token.filteredChildren.get(0);
			Token defaultCaseFactor = token.filteredChildren.get(1);

			builder.n();
			builder.incTab();

			builder.append("(");

			CaseExpressionBuilder.SINGLETON.build(builder, caseExpression);
			builder.n();
			build(builder, defaultCaseFactor);

			builder.append(")");
			builder.decTab();

		} else if (parser instanceof SinParser) {

			Token value = token.filteredChildren.get(0);
			builder.append("(float) Math.sin(calculateContext.radianAngle(");
			build(builder, value);
			builder.append("))");

		} else if (parser instanceof CosParser) {

			Token value = token.filteredChildren.get(0);
			builder.append("(float) Math.cos(calculateContext.radianAngle(");
			build(builder, value);
			builder.append("))");

		} else if (parser instanceof TanParser) {

			Token value = token.filteredChildren.get(0);
			builder.append("(float) Math.tan(calculateContext.radianAngle(");
			build(builder, value);
			builder.append("))");

		} else if (parser instanceof SquareRootParser) {

			Token value = token.filteredChildren.get(0);
			builder.append("(float) Math.sqrt(");
			build(builder, value);
			builder.append(")");

		} else if (parser instanceof MinParser) {

			builder.append("Math.min(");
			build(builder, token.filteredChildren.get(0));
			builder.append(",");
			build(builder, token.filteredChildren.get(1));
			builder.append(")");

		} else if (parser instanceof MaxParser) {

			builder.append("Math.max(");
			build(builder, token.filteredChildren.get(0));
			builder.append(",");
			build(builder, token.filteredChildren.get(1));
			builder.append(")");

		} else if (parser instanceof RandomParser) {

			builder.append("calculateContext.nextRandom()");

		} else if (parser instanceof ToNumParser) {

			Token leftString = token.filteredChildren.get(0);
			Token rightFloatDefault = token.filteredChildren.get(1);

			builder.append("org.unlaxer.tinyexpression.function.EmbeddedFunction.toNum(");
			builder.append(StringClauseBuilder.SINGLETON.build(leftString).toString());
			builder.append(",");
			build(builder, rightFloatDefault);
			builder.append("f)");

		} else if (parser instanceof StringLengthParser) {

			Token stringExpressionToken = token.filteredChildren.get(0);//3rd children is inner
			String string = StringClauseBuilder.SINGLETON.build(stringExpressionToken).toString();
			if(string == null || string.isEmpty()) {
				string ="\"\"";
			}
			builder
				.append(string)
				.append(".length()");

		}else if (parser instanceof SideEffectExpressionParser) {
			
			SideEffectExpressionBuilder.SINGLETON.build(builder, token);
			
//		} else if (parser instanceof StringIndexOfParser) {
//
//			return StringIndexOfOperator.SINGLETON.evaluate(calculateContext, token);
		}else {
			throw new IllegalArgumentException();
		}

	}

	void binaryOperate(SimpleJavaCodeBuilder builder, Token token, String operator) {

		builder.append("(");

		build(builder, token.filteredChildren.get(1));
		builder.append(operator);
		build(builder, token.filteredChildren.get(2));

		builder.append(")");
	}
}