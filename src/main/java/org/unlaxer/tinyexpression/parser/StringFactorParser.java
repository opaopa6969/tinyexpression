package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.ParenthesesParser;

public class StringFactorParser extends LazyChoice implements StringExpression{
	
	private static final long serialVersionUID = -3118310290617698589L;
	
	public StringFactorParser() {
		super();
	}

	List<Parser> parsers;
	
	public static Class<? extends Parser> NESTED = StringExpressionParser.class;
	
	@Override
	public void initialize() {
		
//		.append("StringFactor:=")
//		.append("StringLiteral|")
//		.append("Variable|")
//		.append("'('StringExpression')'|")
//		.append("'trim('StringExpression')'|")
//		.append("'toUpperCase('StringExpression')'|")
//		.append("'toLowerCase('StringExpression')';")
		
		parsers = 
			new Parsers(
				Parser.get(StringLiteralParser.class),
        Parser.get(StringVariableParser.class),
				Parser.get(NakedVariableParser.class),
				new ParenthesesParser(
					Parser.get(NESTED)
				),
				Parser.get(TrimParser.class),
				Parser.get(ToUpperCaseParser.class),
				Parser.get(ToLowerCaseParser.class)
			);
	}


	@Override
	public List<Parser> getLazyParsers() {
		return parsers;
	}
}