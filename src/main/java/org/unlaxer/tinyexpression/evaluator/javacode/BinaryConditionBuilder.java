package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.parser.numbertype.NumberEqualEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberGreaterExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberGreaterOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberLessExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberLessOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberNotEqualExpressionParser;

public class BinaryConditionBuilder implements TokenCodeBuilder{
	
	public static BinaryConditionBuilder SINGLETON = new BinaryConditionBuilder();


	@Override
	public void build(SimpleJavaCodeBuilder builder, Token token,
	    TinyExpressionTokens tinyExpressionTokens) {
		
		Token factor1 = token.filteredChildren.get(0);
		Token factor2 = token.filteredChildren.get(1);
		
		String className = tinyExpressionTokens.numberType().javaTypeAsString();
		
		builder
//    .append("(java.lang.Float.compare(");
    .append("(")
    .append(className)
    .append(".compare(");
		
		NumberExpressionBuilder.SINGLETON.build(builder, factor1 , tinyExpressionTokens);
		
		builder
			.append(",");
		NumberExpressionBuilder.SINGLETON.build(builder, factor2 , tinyExpressionTokens);
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