package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder.Kind;
import org.unlaxer.tinyexpression.parser.stringtype.StringContainsParser;
import org.unlaxer.tinyexpression.parser.stringtype.StringEndsWithParser;
import org.unlaxer.tinyexpression.parser.stringtype.StringStartsWithParser;

public class StringMethodClauseBuilder implements TokenCodeBuilder {

	public static final StringMethodClauseBuilder SINGLETON = new StringMethodClauseBuilder();

	@Override
	public void build(SimpleJavaCodeBuilder builder, Token token , 
	    TinyExpressionTokens tinyExpressionTokens) {
		
		Parser parser = token.parser;
		
		List<Token> filteredChildren = token.filteredChildren;
		
		ExpressionOrLiteral left = StringClauseBuilder.SINGLETON.build(filteredChildren.get(0),
		    tinyExpressionTokens);
		ExpressionOrLiteral right = StringClauseBuilder.SINGLETON.build(filteredChildren.get(1),
		    tinyExpressionTokens);
		
		builder.append("(")
			.append(left.toString());
		if(parser instanceof StringStartsWithParser) {
			builder.append(".startsWith(");
			
		}else if(parser instanceof StringEndsWithParser) {
			builder.append(".endsWith(");
			
		}else if(parser instanceof StringContainsParser) {
			builder.append(".contains(");
		}else {
			throw new IllegalArgumentException();
		}
		builder
			.append(right.toString())
			.append("))");
		
    left.populateTo(builder, Kind.Function);
    right.populateTo(builder, Kind.Function);

	}
}