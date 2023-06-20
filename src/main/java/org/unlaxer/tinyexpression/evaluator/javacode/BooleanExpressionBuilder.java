package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculator.CodeBuilder;
import org.unlaxer.tinyexpression.parser.AndParser;
import org.unlaxer.tinyexpression.parser.EqualEqualParser;
import org.unlaxer.tinyexpression.parser.NotEqualParser;
import org.unlaxer.tinyexpression.parser.OrParser;
import org.unlaxer.tinyexpression.parser.XorParser;

public class BooleanExpressionBuilder implements CodeBuilder {

	static final BooleanExpressionBuilder SINGLETON = new BooleanExpressionBuilder();

	@Override
	public void build(SimpleJavaCodeBuilder builder, Token token) {

		builder.append("(");
		
		Parser parser = token.parser;
		List<Token> filteredChildren = token.filteredChildren;
		
		if(parser instanceof OrParser) {
			
			build(builder, filteredChildren.get(1));
			builder.append("||");
			build(builder, filteredChildren.get(2));
			
		}else if(parser instanceof AndParser) {
			
			build(builder, filteredChildren.get(1));
			builder.append("&&");
			build(builder, filteredChildren.get(2));
			
		}else if(parser instanceof XorParser) {
			
			build(builder, filteredChildren.get(1));
			builder.append("^");
			build(builder, filteredChildren.get(2));
			
		}else if(parser instanceof EqualEqualParser) {
			
			build(builder, filteredChildren.get(1));
			builder.append("==");
			build(builder, filteredChildren.get(2));

		}else if(parser instanceof NotEqualParser) {
			
			build(builder, filteredChildren.get(1));
			builder.append("!=");
			build(builder, filteredChildren.get(2));
			
//		}else if(parser instanceof BooleanExpressionParser) {
		}else {

			BooleanBuilder.SINGLETON.build(builder, token);
			
//		}else {
//			throw new IllegalArgumentException();
		}
		
		builder.append(")");
	}
}