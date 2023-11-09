package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpression;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class IsPresentParser extends JavaStyleDelimitedLazyChain implements BooleanExpression{
	
	private static final long serialVersionUID = -4619955945031421138L;


	public IsPresentParser() {
		super();
	}

	@Override
	public Parsers getLazyParsers() {
	  return
      // IsPresentExpression:='isPresent('Variable');
        new Parsers(
          Parser.get(IsPresentNameParser.class),
          Parser.get(LeftParenthesisParser.class),
          Parser.get(NakedVariableParser.class),//2
          Parser.get(RightParenthesisParser.class)
        );

	}
	
	public static Token getVariable(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(NakedVariableParser.class); //2
	}
}