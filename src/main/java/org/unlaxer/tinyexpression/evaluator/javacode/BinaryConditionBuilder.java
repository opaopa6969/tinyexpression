package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Token;
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
	    TinyExpressionTokens tinyExpressionTokens) {
		
		Token factor1 = token.filteredChildren.get(0);
		Token factor2 = token.filteredChildren.get(1);
		
		NumberExpressionBuilder.SINGLETON.build(builder, factor1 , tinyExpressionTokens);
		
		if(token.parser instanceof NumberEqualEqualExpressionParser) {
			
			builder.append("==");
			
		}else if(token.parser instanceof NumberNotEqualExpressionParser) {
			
			builder.append("!=");
			
		}else if(token.parser instanceof NumberGreaterOrEqualExpressionParser) {
			
			builder.append(">=");
			
		}else if(token.parser instanceof NumberLessOrEqualExpressionParser) {
			
			builder.append("<=");

		}else if(token.parser instanceof NumberGreaterExpressionParser) {
			
			builder.append(">");
			
		}else if(token.parser instanceof NumberLessExpressionParser) {
			
			builder.append("<");
		}else {
			throw new IllegalArgumentException();
		}
		NumberExpressionBuilder.SINGLETON.build(builder, factor2 , tinyExpressionTokens);

	}
	
}