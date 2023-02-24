package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculator.CodeBuilder;
import org.unlaxer.tinyexpression.parser.EqualEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.GreaterExpressionParser;
import org.unlaxer.tinyexpression.parser.GreaterOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.LessExpressionParser;
import org.unlaxer.tinyexpression.parser.LessOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.NotEqualExpressionParser;

public class BinaryConditionBuilder implements CodeBuilder{
	
	public static BinaryConditionBuilder SINGLETON = new BinaryConditionBuilder();


	@Override
	public void build(SimpleJavaCodeBuilder builder, Token token) {
		
		Token factor1 = token.filteredChildren.get(0);
		Token factor2 = token.filteredChildren.get(1);
		
		ExpressionBuilder.SINGLETON.build(builder, factor1);
		
		if(token.parser instanceof EqualEqualExpressionParser) {
			
			builder.append("==");
			
		}else if(token.parser instanceof NotEqualExpressionParser) {
			
			builder.append("!=");
			
		}else if(token.parser instanceof GreaterOrEqualExpressionParser) {
			
			builder.append(">=");
			
		}else if(token.parser instanceof LessOrEqualExpressionParser) {
			
			builder.append("<=");

		}else if(token.parser instanceof GreaterExpressionParser) {
			
			builder.append(">");
			
		}else if(token.parser instanceof LessExpressionParser) {
			
			builder.append("<");
		}else {
			throw new IllegalArgumentException();
		}
		ExpressionBuilder.SINGLETON.build(builder, factor2);

	}
	
}