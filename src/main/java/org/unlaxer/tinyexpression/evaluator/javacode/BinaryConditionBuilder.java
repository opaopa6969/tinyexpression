package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.NumberEqualEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberGreaterExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberGreaterOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberLessExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberLessOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberNotEqualExpressionParser;

public class BinaryConditionBuilder implements TokenCodeBuilder{
	
	public static BinaryConditionBuilder SINGLETON = new BinaryConditionBuilder();


	@Override
	public void build(SimpleJavaCodeBuilder builder, Token token,
	    TinyExpressionTokens tinyExpressionTokens, ExpressionType resultType) {
		
		Token factor1 = token.filteredChildren.get(0);
		Token factor2 = token.filteredChildren.get(1);
		
		String className = resultType.javaTypeAsString();
		
		builder
//    .append("(java.lang.Float.compare(");
    .append("(")
    .append(className)
    .append(".compare(");
		
		NumberExpressionBuilder.SINGLETON.build(builder, factor1 , tinyExpressionTokens , resultType);
		
		builder
			.append(",");
		NumberExpressionBuilder.SINGLETON.build(builder, factor2 , tinyExpressionTokens , resultType);
		builder
			.append(")");

		
		if(token.parser instanceof NumberEqualEqualExpressionParser) {
			
//			builder.append(" == ");
			builder.append(" == 0");
			
			
		}else if(token.parser instanceof NumberNotEqualExpressionParser) {
			
//			builder.append("!=");
			builder.append(" != 0");
			
		}else if(token.parser instanceof NumberGreaterOrEqualExpressionParser) {
			
//			builder.append(">=");
			builder.append(" >= 0");
			
		}else if(token.parser instanceof NumberLessOrEqualExpressionParser) {
			
//			builder.append("<=");
			builder.append(" <= 0");


		}else if(token.parser instanceof NumberGreaterExpressionParser) {
			
//			builder.append(">");
			builder.append(" > 0");

			
		}else if(token.parser instanceof NumberLessExpressionParser) {
			
//			builder.append("<");
			builder.append(" < 0");

		}else {
			throw new IllegalArgumentException();
		}
		builder
			.append(")");
	}
	
}